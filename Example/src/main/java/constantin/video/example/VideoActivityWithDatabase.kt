package constantin.video.example

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.ArrayMap
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

import constantin.video.core.DecodingInfo
import constantin.video.core.IVideoParamsChanged
import constantin.video.core.video_player.VideoSettings
import constantin.video.core.external.AspectFrameLayout
import constantin.video.core.video_player.VideoPlayer
import constantin.video.impl.SimpleVideoActivity

const val ID_OS_VERSIONS : String ="OSVersions";
const val ID_BUILD_MODEL : String ="BuildModel";
const val ID_BUILD_DEVICE : String ="BuildDevice";
const val ID_BUILD_MANUFACTURER : String = "BuildManufacturer";

class VideoActivityWithDatabase : SimpleVideoActivity(),SurfaceHolder.Callback {
    private val TAG = this::class.java.simpleName
    private lateinit var context: Context;

    private var VS_SOURCE: Int = 0
    private var VS_ASSETS_FILENAME_TEST_ONLY: String? = ""
    private val DECODING_INFO: String ="DecodingInfo2";


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val preferences = getSharedPreferences("pref_video", Context.MODE_PRIVATE)
        VS_SOURCE = preferences.getInt(getString(R.string.VS_SOURCE), 0)
        VS_ASSETS_FILENAME_TEST_ONLY = preferences.getString(getString(R.string.VS_ASSETS_FILENAME_TEST_ONLY), "")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    public override fun onDestroy() {
        super.onDestroy()
        writeTestResult();
    }

    private fun writeTestResult() {
        if (mDecodingInfo != null && mDecodingInfo!!.nNALUSFeeded > 120) {
            val db = FirebaseFirestore.getInstance()
            val mTestResult=TestResultDecodingInfoConstructor.create(VS_SOURCE,(if (VS_SOURCE == VideoSettings.VS_SOURCE.ASSETS.ordinal)
                VS_ASSETS_FILENAME_TEST_ONLY
            else
                "Unknown")!!,mDecodingInfo!!);
            val writeBatch = db.batch()
            //val allCombinationsReference=db.collection(DECODING_INFO).document("ALL_DEVICE_OS_COMBINATIONS")
            //writeBatch.set(allCombinationsReference,"",FieldValue.arrayUnion(Helper.getBuildVersionRelease()),SetOptions.merge())
            val thisDeviceReference = db.collection(DECODING_INFO).document(Helper.getManufacturerAndDeviceName())
            val dummyMap = ArrayMap<String, Any>()
            //dummyMap["OsVersions."+Helper.getBuildVersionRelease().replace(".","_")] = null
            dummyMap[ID_BUILD_MODEL] = Build.MODEL
            dummyMap[ID_BUILD_MANUFACTURER] = Build.MANUFACTURER
            dummyMap[ID_BUILD_DEVICE]=Build.DEVICE
            writeBatch.set(thisDeviceReference, dummyMap, SetOptions.merge())
            writeBatch.update(thisDeviceReference,ID_OS_VERSIONS,FieldValue.arrayUnion(Helper.getBuildVersionRelease()))
            val thisDeviceOsNewTestData = thisDeviceReference.collection(Helper.getBuildVersionRelease()).document()
            writeBatch.set(thisDeviceOsNewTestData, mTestResult)
            //writeBatch.update(thisDeviceOsNewTestData,mDecodingInfo!!.toMap());
            writeBatch.commit().addOnSuccessListener {
                Log.d(TAG, "WriteBatch added DocumentSnapshot with id: " + thisDeviceOsNewTestData.id)
            }
        }
    }



}
