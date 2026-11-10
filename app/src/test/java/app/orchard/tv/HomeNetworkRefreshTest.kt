package app.orchard.tv

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.orchard.tv.system.NetKind
import kotlinx.coroutines.cancelChildren
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A network callback may arrive while a TV's input service is still answering the resume probe. */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [SlowTvInputs::class])
class HomeNetworkRefreshTest {
    @Test fun wifiConnectingDuringSystemRefreshIsNotOverwritten() = checkTransition(false, true)

    @Test fun wifiDisconnectingDuringSystemRefreshIsNotOverwritten() = checkTransition(true, false)

    private fun checkTransition(before: Boolean, after: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).setSystemService(Context.TV_INPUT_SERVICE, Shadow.newInstanceOf(TvInputManager::class.java))
        app.getSharedPreferences("settings", 0).edit().putBoolean("setup_offered", true).commit()
        val vm = HomeViewModel(app)
        val store = ViewModelStore().apply { put("home", vm) }
        val gate = SlowTvInputs.Gate()
        try {
            // Cancel the unrelated app/artwork loading started by the constructor.
            vm.viewModelScope.coroutineContext.cancelChildren()
            await { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.none() }
            setWifi(before)
            vm.refreshNetwork()
            SlowTvInputs.next.set(gate)
            vm.refreshSystem()
            await { gate.entered.count == 0L }

            setWifi(after)
            vm.refreshNetwork() // The same update delivered by MainActivity's network callback.
            val expected = if (after) NetKind.Wifi else NetKind.Offline
            assertEquals(expected, vm.network.kind)
            gate.release.countDown()
            // Volume is assigned after the input, home and brightness reads have all returned.
            await { vm.volume.max != 100 }
            assertEquals(expected, vm.network.kind)
        } finally {
            gate.release.countDown()
            SlowTvInputs.next.set(null)
            store.clear()
        }
    }

    private fun setWifi(connected: Boolean) {
        val cm = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val connectivity = shadowOf(cm)
        connectivity.clearAllNetworks()
        connectivity.setActiveNetworkInfo(null)
        if (connected) {
            val info = ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED,
            )
            connectivity.setActiveNetworkInfo(info)
            val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
            connectivity.setNetworkCapabilities(network, ShadowNetworkCapabilities.newInstance().apply {
                shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            })
        }
    }

    private fun await(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Background system probe did not finish", done())
    }
}

@Implements(TvInputManager::class)
class SlowTvInputs {
    class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    @Implementation fun getTvInputList(): List<TvInputInfo> {
        next.getAndSet(null)?.let {
            it.entered.countDown()
            check(it.release.await(10, TimeUnit.SECONDS)) { "Input-service gate was not released" }
        }
        return emptyList()
    }

    companion object {
        val next = AtomicReference<Gate?>()
    }
}
