package gt.hack.nfc

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen

class HackGTApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    AndroidThreeTen.init(this)
  }
}