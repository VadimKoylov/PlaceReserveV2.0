package com.example.placereserve

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import com.google.firebase.database.*
import com.onlylemi.mapview.library.MapViewListener
import com.onlylemi.mapview.library.layer.BitmapLayer
import kotlinx.android.synthetic.main.activity_place.*
import com.google.firebase.database.DataSnapshot
import com.onlylemi.mapview.library.MapView


class CustomMapViewListener(private var placeActivity: PlaceActivity, private var currentMap: MapView) : MapViewListener {
    var isLoader:Boolean = false
    private val database = FirebaseDatabase.getInstance()
    var bitmapChoosed: BitmapLayer? = null
    private var TAG: String = "CustomMapViewListener"

    override fun onMapLoadSuccess() {
        isLoader = true

        val myRef = database.getReference("Заведения").child(placeActivity.intent.getStringExtra("place_name")).child("Столы")
        createTables(myRef)
    }

    override fun onMapLoadFail() {
        Log.e(TAG, "Ah shit, here we go again")
    }

    fun drawDick(x:Float, y:Float){
        if(!isLoader) return
        val freeBmp = BitmapFactory.decodeResource(placeActivity.resources, R.drawable.free_1)

        val fixedFreeBmp = Bitmap.createScaledBitmap(freeBmp, 150, 150, false)
        var bitmapLayer = BitmapLayer(currentMap, fixedFreeBmp)
        bitmapLayer!!.location = PointF(x, y)
        bitmapLayer!!.isAutoScale = true
        bitmapLayer!!.setOnBitmapClickListener { // Вешаем на кнопку слушатель кликбейта за сто морей
            var tagValue = PlaceActivity.SELECTED

            if(bitmapChoosed != null) { // Если предыдущий стол выбран
                if(bitmapChoosed!!.equals(bitmapLayer)) { // Если это один и тот же стол. то отменяем галку
                    tagValue = PlaceActivity.UNSELECTED
                    bitmapChoosed!!.bitmap = fixedFreeBmp
                    bitmapChoosed = null
                    placeActivity.sit_count.text = "- место"
                    Toast.makeText(
                        placeActivity,
                        "Отмена",
                        Toast.LENGTH_SHORT
                    ).show()
                } else { // если это другой стол
                    bitmapChoosed!!.bitmap = fixedFreeBmp // обозначем занятой стол свободным
                    bitmapChoosed = bitmapLayer // и обозначем новый стол занятым
                    val choosed = BitmapFactory.decodeResource(placeActivity.resources, R.drawable.choosedtable)
                    val choosedBmp = Bitmap.createScaledBitmap(choosed, 150, 150, false)
                    bitmapChoosed!!.bitmap = choosedBmp
                    placeActivity.sit_count.text = "1 место"

                    Toast.makeText(
                        placeActivity,
                        "Место выбрано",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                bitmapChoosed = bitmapLayer

                val choosed = BitmapFactory.decodeResource(placeActivity.resources, R.drawable.choosedtable)
                val choosedBmp = Bitmap.createScaledBitmap(choosed, 150, 150, false)
                bitmapChoosed!!.bitmap = choosedBmp
                placeActivity.sit_count.text = "1 место"

                Toast.makeText(
                    placeActivity,
                    "Место выбрано",
                    Toast.LENGTH_SHORT
                ).show()
            }
            placeActivity.intent.putExtra(PlaceActivity.SELECTED_TAG, tagValue)
            placeActivity.updateButton(PlaceActivity.CHOOSE_PAGE)
            currentMap!!.refresh()
        }
        currentMap!!.addLayer(bitmapLayer)
        currentMap!!.refresh()
    }

    /**
     * database должен начинаться с чайлада "Столы"
     */
    fun createTables(database: DatabaseReference) {
        if(!isLoader) return
        var tree = database.child("Номер стола")

        tree.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (ds in dataSnapshot.children) {
                    val x = ds.child("Координаты").child("x").value.toString()
                    val y = ds.child("Координаты").child("y").value.toString()
                    drawDick(x!!.toFloat(),y!!.toFloat())
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Ah shit, here we go again")
            }
        })
    }
}