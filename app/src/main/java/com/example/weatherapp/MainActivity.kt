package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.weatherapp.Models.WeatherResponse
import com.example.weatherapp.Networks.WeatherService
import com.google.android.gms.location.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences
    private var mTopic : String = "sunny"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        setUpUI()

        refreshLayout.setOnRefreshListener {
            requestLocationData()
            refreshLayout.isRefreshing = false
        }

        if(!isLocationEnabled()){
            Toast.makeText(this,
                "Your location provider is turned OFF. Please turn it ON to continue",
                Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener(object: MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }

                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,
                                "You've denied location permissions. Please enable it to continue further",
                                Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }

        //For Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel: NotificationChannel = NotificationChannel(
                "MyNotifications",
                "MyNotifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        Firebase.messaging.subscribeToTopic(mTopic)
            .addOnCompleteListener { task ->
                var msg = "Weather Updated"
                if (!task.isSuccessful) {
                    msg = "Failed"
                }
                Log.d("Msg Firebase ", msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }

        btn_search_city.setOnClickListener {
            if(city_name.text.toString().trim().isNotEmpty()){
                getLocationWeatherDetailsUsingCityName(city_name.text.toString().trim())
            }
        }

    }

    private fun getLocationWeatherDetailsUsingCityName(q: String){
        Log.i("City Name", "$q")
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeatherFromCityName(q, Constants.APP_ID)

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList) //this return a string
                        Log.i("JSON Result","$weatherResponseJsonString")
                        val editor = mSharedPreferences.edit()
                        //Value will be stored corresponding to key WEATHER_RESPONSE_DATA
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        Log.i("Response Result", "$weatherList")
                        setUpUI()
                    }else{
                        hideCustomProgressDialog()
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                                Toast.makeText(this@MainActivity,"City Not found",Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                Log.e("Error", "${response.code()}")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    Log.e("Error/Failure", t!!.message.toString())
                    hideCustomProgressDialog()
                }

            })
        }else{
            Toast.makeText(this, "Internet Not Connected",Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList) //this return a string
                        val editor = mSharedPreferences.edit()
                        //Value will be stored corresponding to key WEATHER_RESPONSE_DATA
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        Log.i("Response Result", "$weatherList")
                        setUpUI()
                    }else{
                        hideCustomProgressDialog()
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "${response.code()}")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    Log.e("Error/Failure", t!!.message.toString())
                    hideCustomProgressDialog()
                }

            })
        }else{
            Toast.makeText(this, "Internet Not Connected",Toast.LENGTH_SHORT).show()
        }
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current latitude", "$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current longitude", "$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()
        )
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("Oops. The required permissions are turned OFF. Please enable under application settings")
            .setPositiveButton(
                "Go to Settings"
            ){
                _, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e : Exception){
                    e.printStackTrace()
                }
            }
            .setNegativeButton(
                "Cancel"
            ){
                dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {

        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.custom_dialog_progress)
        mProgressDialog!!.show()
    }
    private fun hideCustomProgressDialog(){
        if(mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            Log.i("json result",weatherResponseJsonString)
            for(i in weatherList.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)
                tv_humidity.text = weatherList.main.humidity.toString() + "%"
                tv_min.text = weatherList.main.temp_min.toString() + "Min"
                tv_max.text = weatherList.main.temp_max.toString() + "Max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                when(weatherList.weather[i].icon){
                    "01d" -> {
                        iv_main.setImageResource(R.drawable.sunny)
                        background_image.setImageResource(R.drawable.sunny_background)
                        mTopic = "sunny"
                    }
                    "02d" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "03d" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.scatteredclouds_background)
                        mTopic = "cloud"
                    }
                    "04d" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "04n" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.fewclouds_background)
                        mTopic = "cloud"

                    }
                    "09d" -> {
                        iv_main.setImageResource(R.drawable.rain)
                        background_image.setImageResource(R.drawable.showerrain_background)
                        mTopic = "rain"
                    }
                    "10d" -> {
                        iv_main.setImageResource(R.drawable.rain)
                        background_image.setImageResource(R.drawable.rainy_background)
                        mTopic = "rain"
                    }
                    "11d" -> {
                        iv_main.setImageResource(R.drawable.storm)
                        background_image.setImageResource(R.drawable.thunderstorm_background)
                        mTopic = "storm"
                    }
                    "13d" -> {
                        iv_main.setImageResource(R.drawable.snowflake)
                        background_image.setImageResource(R.drawable.snow_background)
                        mTopic = "snow"
                    }
                    "01n" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "02n" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "03n" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "10n" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.cloudy_background)
                        mTopic = "cloud"
                    }
                    "11n" -> {
                        iv_main.setImageResource(R.drawable.rain)
                        background_image.setImageResource(R.drawable.rainy_background)
                        mTopic = "rain"
                    }
                    "13n" -> {
                        iv_main.setImageResource(R.drawable.snowflake)
                        background_image.setImageResource(R.drawable.snow_background)
                        mTopic = "snow"
                    }
                    "50d" -> {
                        iv_main.setImageResource(R.drawable.cloud)
                        background_image.setImageResource(R.drawable.mist_background)
                        mTopic = "snow"
                    }
                }
            }
        }


    }

    @Suppress("NAME_SHADOWING")
    private fun getUnit(value: String) : String {
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long) : String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }



//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.menu_main, menu)
//        return super.onCreateOptionsMenu(menu)
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//
//        return when(item.itemId){
//            R.id.action_refresh -> {
//                requestLocationData()
//                true
//            }
//            else -> super.onOptionsItemSelected(item)
//        }
//    }
}