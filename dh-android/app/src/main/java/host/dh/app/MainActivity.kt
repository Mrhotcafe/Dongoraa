package host.dh.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // DH CONTROLLED DEFECT: force a runtime crash on launch
    val boom: String? = null
    findViewById<TextView>(R.id.headline).text = boom!!.length.toString()
  }
}
