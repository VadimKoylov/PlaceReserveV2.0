package com.example.placereserve

import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import com.example.placereserve.TableIconsCache.Companion.busyIconBmp
import com.example.placereserve.TableIconsCache.Companion.choosedIconBmp
import com.example.placereserve.TableIconsCache.Companion.freeIconBmp
import com.google.firebase.database.*
import com.onlylemi.mapview.library.MapViewListener
import com.onlylemi.mapview.library.layer.BitmapLayer
import kotlinx.android.synthetic.main.activity_place.*
import com.google.firebase.database.DataSnapshot
import com.onlylemi.mapview.library.MapView


class CustomMapViewListener(private var placeActivity: PlaceActivity, private var currentMap: MapView) :
    MapViewListener {
    var isLoader: Boolean = false
    private val database = FirebaseDatabase.getInstance()

    var bitmapChoosed: BitmapLayer? = null
    var choosedTableNumber = 0
    var checkDateReservationUser = ""
//    lateinit var firebaseAuth: FirebaseAuth
//    val user = firebaseAuth.currentUser
    val user = placeActivity.firebaseAuth.currentUser
    var tableList = hashMapOf<Int, BitmapLayer>() // id table, bitmap layer

    private var TAG: String = "CustomMapViewListener"

    override fun onMapLoadSuccess() {
        isLoader = true

        val myRef = database.getReference("Заведения").child(placeActivity.intent.getStringExtra("place_name")).child("Столы")
        createTables(myRef)
    }

    override fun onMapLoadFail() {
        Log.e(TAG, "Ah shit, here we go again")
    }

    fun checkDateReservationUser (){
        var ref = database.getReference("Пользователи").child(user!!.phoneNumber!!).child("Активные брони")
            .child(placeActivity.intent.getStringExtra("place_name")).child(placeActivity.intent.getStringExtra("place_address")).child(placeActivity.date)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                checkDateReservationUser = dataSnapshot.getValue().toString()
                ref.removeEventListener(this) // LeakCanary fix
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                ref.removeEventListener(this) // LeakCanary fix
            }
        })
    }

    /**
     * Создаем столы на карте. ПРОСТО создаем столы на карте, без всяких проверок в БД.
     * Обновление карты не производятся, делайте вручную.
     */

    private fun createTable(id:Int, x:Float, y:Float) {
        if (!isLoader) return
        var bitmapLayer = BitmapLayer(currentMap, freeIconBmp) // default free table
        bitmapLayer!!.location = PointF(x, y)
        bitmapLayer!!.isAutoScale = true
        bitmapLayer!!.setOnBitmapClickListener { // Вешаем на кнопку слушатель кликбейта за сто морей
            if (bitmapLayer.bitmap == busyIconBmp){ // Если стол занят другим членом
                Toast.makeText(
                    placeActivity.applicationContext,
                    "Этот стол занят, выберите другой стол.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnBitmapClickListener
            }

            //if(!checkDateReservationUser.isEmpty() ){
            /**
             * Если в БД нет нужного элемента, то Firebase возвращает просто 'null'. так что чекать через isEmpty не оч
             */
            if (placeActivity.intent.getStringExtra("place_status")=="1") {
                if (checkDateReservationUser != "null") { // Если челик уже занял столик

                    var maskDateHis = placeActivity.date.replace(' ', '/')
                    Toast.makeText(
                        placeActivity.applicationContext,
                        "Вы уже забронировали 1 стол на эту дату ($maskDateHis).",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnBitmapClickListener
                }
            }
            var tagValue = PlaceActivity.SELECTED
            val choosedId = id
            placeActivity.id_stola = choosedId
            placeActivity.sit_count.text = "Выбран стол № $choosedId"
            if(bitmapChoosed != null) { // Если предыдущий стол выбран
                if(bitmapChoosed!!.equals(bitmapLayer)) { // Если это один и тот же стол. то отменяем галку
                    tagValue = PlaceActivity.UNSELECTED
                    bitmapChoosed!!.bitmap = freeIconBmp
                    bitmapChoosed = null
                    //placeActivity.sit_count.text = "Выбран стол № $choosedId"   //  placeActivity.sit_count.text = "- место"
                    choosedTableNumber = 0
                    placeActivity.sit_count.text = ""
                } else { // если это другой стол
                    bitmapChoosed!!.bitmap = freeIconBmp // обозначем занятой стол свободным
                    updateTable(choosedTableNumber) // обновляем текущий статус с БД
                    bitmapChoosed = bitmapLayer // и обозначем новый стол занятым
                    bitmapChoosed!!.bitmap = choosedIconBmp
                  //  placeActivity.sit_count.text = "Выбран стол № $choosedId"
                    choosedTableNumber = choosedId


                }
            } else {
                bitmapChoosed = bitmapLayer
                bitmapChoosed!!.bitmap = choosedIconBmp
                //placeActivity.sit_count.text = "1 место"
                choosedTableNumber = choosedId


            }
            placeActivity.intent.putExtra(PlaceActivity.SELECTED_TAG, tagValue)
            placeActivity.updateButton(PlaceActivity.CHOOSE_PAGE)
            currentMap!!.refresh()

        }
        currentMap!!.addLayer(bitmapLayer)
        tableList[id] = bitmapLayer
    }

    /**
     * Создаем столы. database должен начинаться с чайлада "Столы"
     */
    fun createTables(database: DatabaseReference) {
        if(!isLoader) return
        checkDateReservationUser()
        var tree = database.child("Номер стола")

        tree.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (ds in dataSnapshot.children) {
                    val id = ds.key
                    val x = ds.child("Координаты").child("x").value.toString()
                    val y = ds.child("Координаты").child("y").value.toString()
                    val reserved =
                        ds.child("Бронь").child("Дата").child(placeActivity.date).child("Забронирован").value.toString()
                    createTable(id!!.toInt(), x.toFloat(),y.toFloat()) // сначала создаем столы на карте
                    updateTable(id!!.toInt(), reserved.toBoolean()) // затем обновляем картиночки с БД
                }

                currentMap!!.refresh() // вместо +100500 обновлений рендера карты, вызывайте одну вне цикла.
                //after all remove listener
                tree.removeEventListener(this)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                tree.removeEventListener(this)
                Log.e(TAG, "Ah shit, here we go again")
            }
        })
    }

    /**
     * Очищает все столы с карты, которые есть в HashMap. Обновление карты выполняйте вручную.
     * Выполняйте чистку только во время БЕЗДЕЙСТВИЯ со стороны пользователя, иначе получите Fatal Error.
     */
    fun clearAllTable() {
        if(!isLoader) return
        for(layer in tableList.values) {
            currentMap.layers.remove(layer)
        }
    }

    /**
     * Меняет иконку стола с БД
     */
    fun updateTable(id:Int) {
        if(!isLoader) return
        if(!tableList.containsKey(id)) {
            Log.e(TAG,"table id is not exists!")
            return
        }

        val myRef = database.getReference("Заведения").child(placeActivity.intent.getStringExtra("place_name")).child("Столы")
        var table = myRef.child("Номер стола").child(id.toString())

        table.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val reserved =
                    dataSnapshot.child("Бронь").child("Дата").child(placeActivity.date).child("Забронирован").value.toString()
                updateTable(id, reserved.toBoolean())
                currentMap!!.refresh()

                //after all remove listener
                table.removeEventListener(this)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                table.removeEventListener(this)
                Log.e(TAG, "Ah shit, here we go again")
            }
        })
    }

    /**
     * Меняет иконку стола. Обновление карты выполняйте вручную.
     */
    private fun updateTable(id:Int, reserved: Boolean) {
        if(!isLoader) return
        if(!tableList.containsKey(id)) {
            Log.e(TAG,"table id is not exists!")
            return
        }

        var bitmapLayer = tableList[id]
        if(reserved) {
            bitmapLayer!!.bitmap = busyIconBmp
        } else {
            bitmapLayer!!.bitmap = freeIconBmp
        }
    }

    /**
     * Update all tables
     */
    fun updateTables() {
        if(!isLoader) return
        checkDateReservationUser()
        val myRef = database.getReference("Заведения").child(placeActivity.intent.getStringExtra("place_name")).child("Столы")
        updateTables(myRef)
    }

    /**
     * Update all tables from specific database
     */
    private fun updateTables(database: DatabaseReference) {
        var tree = database.child("Номер стола")
        tree.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (ds in dataSnapshot.children) {
                    val id = ds.key
                    val reserved =
                        ds.child("Бронь").child("Дата").child(placeActivity.date).child("Забронирован").value.toString()
                    updateTable(id!!.toInt(), reserved.toBoolean())
                }

                currentMap!!.refresh()
                //after all remove listener
                tree.removeEventListener(this)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                tree.removeEventListener(this)
                Log.e(TAG, "Ah shit, here we go again")
            }
        })
    }
}