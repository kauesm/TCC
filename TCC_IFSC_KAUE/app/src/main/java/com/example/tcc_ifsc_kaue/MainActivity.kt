package com.example.tcc_ifsc_kaue

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.Global.getString
import android.service.autofill.SavedDatasetsInfo
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.core.view.get
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ingenieriajhr.blujhr.BluJhr
import com.jjoe64.graphview.series.DataPoint
import kotlinx.android.synthetic.main.activity_main.*
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.*
import java.nio.file.Paths.get


class MainActivity : AppCompatActivity() {

    /**
     * Variáveis_____________________________________________________________________________
     */

    /**
     * Bluetooth
     */

    //bluetooth
    lateinit var blue: BluJhr
    var devicesBluetooth = ArrayList<String>()
    //Armazena o estado atual da conexão Bluetooth
    var stateConn = BluJhr.Connected.False

    /**
     * Do Gráfico
     */

    //visible ListView
    var graphviewVisible = true
    //graphviewSeries
    lateinit var temperatura: LineGraphSeries<DataPoint?>
    lateinit var potenciometro:LineGraphSeries<DataPoint>
    //Indica se está recebendo dados
    var initGraph = false
    //valor que se suma al eje x despues de cada actualizacion
    var ejeX = 0.6

    /**
     * Do Sweet (alertas)
     */

    //sweet alert necesarios
    lateinit var loadSweet : SweetAlertDialog
    lateinit var errorSweet : SweetAlertDialog
    lateinit var okSweet : SweetAlertDialog
    lateinit var disconnection : SweetAlertDialog

    //lista que aceita diferentes variaveis
    val names = mutableListOf<String>()

    private lateinit var dbRef: DatabaseReference


    /**
     * On Crate______________________________________________________________________________
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.sleep(5000)
        setTheme(R.style.Theme_TCC_IFSC_KAUE)
        setContentView(R.layout.activity_main)

        val firebase: DatabaseReference = FirebaseDatabase.getInstance().getReference()


        //inicia variaveis do sweetAlert
        initSweet()

        blue = BluJhr(this)
        blue.onBluetooth()

        //clicando em Aparelho
        btnViewDevice.setOnClickListener {
            when (graphviewVisible) {
                false -> invisibleListDevice()
                true -> visibleListDevice()
            }
        }

        //clicando no Dispositivo
        listDeviceBluetooth.setOnItemClickListener { adapterView, view, i, l ->
            if (devicesBluetooth.isNotEmpty()) {
                blue.connect(devicesBluetooth[i])
                //genera error si no se vuelve a iniciar los objetos sweet
                initSweet()
                blue.setDataLoadFinishedListener(object : BluJhr.ConnectedBluetooth {
                    override fun onConnectState(state: BluJhr.Connected) {
                        stateConn = state
                        when (state) {
                            BluJhr.Connected.True -> {
                                loadSweet.dismiss()
                                okSweet.show()
                                invisibleListDevice()
                                rxReceived()
                            }

                            BluJhr.Connected.Pending -> {
                                loadSweet.show()
                            }

                            BluJhr.Connected.False -> {
                                loadSweet.dismiss()
                                errorSweet.show()
                            }

                            BluJhr.Connected.Disconnect -> {
                                loadSweet.dismiss()
                                disconnection.show()
                                visibleListDevice()
                            }

                        }
                    }
                })
            }
        }

        //graphview
        initGraph()

        //clicando em parar
        btnInitStop.setOnClickListener {
            if (stateConn == BluJhr.Connected.True){
                initGraph = when(initGraph){
                    true->{
                        blue.bluTx("0")
                        btnInitStop.text = "Iniciar"
                        false
                    }
                    false->{
                        blue.bluTx("1")
                        btnInitStop.text = "Parar"
                        true
                    }
                }
            }
        }

    }
    /**
     * On Crate______________________________________________________________________________
     */

    /**
     * Firebase (salvar na nuvem)___________________________________
     */
    private fun saveGraphData(){

        dbRef = FirebaseDatabase.getInstance().getReference("Caminho")

        //getting values


        val empTemp = txtTemp.text.toString()
        val empPot = txtPot.text.toString()

        val empId = dbRef.push().key!!
        val bancoDeDados = BancoDeDados(empId, empTemp, empPot)

        dbRef.child(empId).setValue(bancoDeDados)
            .addOnCompleteListener{
                Toast.makeText(this, "Salvando", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener{ err ->
                Toast.makeText(this, "Houve falha", Toast.LENGTH_SHORT).show()
            }


    }

/**
    private fun saveData(){
        val insertedText= txtTemp.text.toString()
        listDados.text = insertedText


        val sharedPreferences =getSharedPreferences("sharedPref",Context.MODE_PRIVATE)
        val editor=sharedPreferences.edit()
        editor.apply {
            putString("STRING_KEY", insertedText)
            putBoolean("BOOLEAN_KEY",sw_switch.isChecked)
        }.apply()
        Toast.makeText(this, "Dados salvos",Toast.LENGTH_SHORT).show()
    }

    private fun loadData(){
        val sharedPreferences =getSharedPreferences("sharedPref",Context.MODE_PRIVATE)
        val savedString=sharedPreferences.getString("STRING_KEY",null)
        val savedBoolean=sharedPreferences.getBoolean("BOOLEAN_KEY",false)

        listDados.text = savedString
        names.plus(savedString)
        sw_switch.isChecked = savedBoolean
    }
 */

    /**
     * Sweet (carregamento e avisos)___________________________________
     */
    private fun initSweet() {
        loadSweet = SweetAlertDialog(this,SweetAlertDialog.PROGRESS_TYPE)
        okSweet = SweetAlertDialog(this,SweetAlertDialog.SUCCESS_TYPE)
        errorSweet = SweetAlertDialog(this,SweetAlertDialog.ERROR_TYPE)
        disconnection = SweetAlertDialog(this,SweetAlertDialog.NORMAL_TYPE)

        loadSweet.titleText = "Conectando"
        loadSweet.setCancelable(false)
        errorSweet.titleText = "Houve uma falha"

        okSweet.titleText = "Conectado"
        disconnection.titleText = "Desconectado"
    }

    /**
     * Funcionamento do Gráfico________________________________________
     */
    private fun initGraph() {
        //permitime controlar los ejes manualmente
        graph.viewport.isXAxisBoundsManual = true;
        graph.viewport.isYAxisBoundsManual = true;
        graph.viewport.setMinX(0.0);
        graph.viewport.setMaxX(10.0);
        graph.viewport.setMaxY(250.0)
        graph.viewport.setMinY(0.0)

        //permite realizar zoom y ajustar posicion eje x
        graph.viewport.isScalable = true
        graph.viewport.setScalableY(true)

        temperatura = LineGraphSeries()
        //draw points
        temperatura.isDrawDataPoints = true;
        //draw below points
        temperatura.isDrawBackground = true;
        //color series
        temperatura.color = Color.RED

        potenciometro = LineGraphSeries()
        //draw points
        potenciometro.isDrawDataPoints = true;
        //draw below points
        potenciometro.isDrawBackground = true;
        //color series
        potenciometro.color = Color.BLUE

        graph.addSeries(temperatura);
        graph.addSeries(potenciometro)

    }

    /**
     * Recebe os dados via Bluetooth___________________________________
     */
    private fun rxReceived() {
        blue.loadDateRx(object:BluJhr.ReceivedData{
            override fun rxDate(rx: String) {
                println("------------------- RX $rx --------------------")
                ejeX+=0.6
                if (rx.contains("t")){
                    val date = rx.replace("t","")
                    txtTemp.text = "Ilu: $date"
                    temperatura.appendData(DataPoint(ejeX, date.toDouble()), true, 22)
                    //Carrega os dados para a memória do celular
                    //loadData()
                    //saveData()
                    saveGraphData()

                }else{
                    if (rx.contains("p")){
                        val date = rx.replace("p","")
                        txtPot.text = "Pot: $date"
                        potenciometro.appendData(DataPoint(ejeX, date.toDouble()), true, 22)
                    }
                }

            }
        })
    }


    /**
     * Controle dos Botões_____________________________________________
     */

    /**
     * invisible listDevice
     */
    private fun invisibleListDevice() {
        containerGraph.visibility = View.VISIBLE
        containerDevice.visibility = View.GONE
        //containerDados.visibility = View.GONE
        graphviewVisible = true
        btnViewDevice.text = "Dispositivo"
    }

    /**
     * visible list device
     */
    private fun visibleListDevice() {
        containerGraph.visibility = View.GONE
        containerDevice.visibility = View.VISIBLE
        //containerDados.visibility = View.GONE
        graphviewVisible = false
        btnViewDevice.text = "Gráfico"

    }

    /**
     * Invisible list Dados

    private fun invisibleListDados() {
        containerGraph.visibility = View.VISIBLE
        containerDevice.visibility = View.GONE
        //containerDados.visibility = View.GONE
        graphviewVisible = true
        btnViewDevice.text = "Gráfico"
    }
     */

    /**
     * Visible list Dados
     */
    private fun visibletDados() {
        containerGraph.visibility = View.GONE
        containerDevice.visibility = View.GONE
        //containerDados.visibility = View.VISIBLE
        graphviewVisible = false
        btnViewDevice.text = "Voltar"
    }


    /**
     * Funcionamento do Bluetooth _____________________________________________
     */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!blue.stateBluetoooth() && requestCode == 100){
            blue.initializeBluetooth()
        }else{
            if (requestCode == 100){
                devicesBluetooth = blue.deviceBluetooth()
                if (devicesBluetooth.isNotEmpty()){
                    val adapter = ArrayAdapter(this,android.R.layout.simple_expandable_list_item_1,devicesBluetooth)
                    listDeviceBluetooth.adapter = adapter
                }else{
                    Toast.makeText(this, "Não tem dispositivo vinculado", Toast.LENGTH_SHORT).show()
                }

            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (blue.checkPermissions(requestCode,grantResults)){
            Toast.makeText(this, "Sair", Toast.LENGTH_SHORT).show()
            blue.initializeBluetooth()
        }else{
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
                blue.initializeBluetooth()
            }else{
                Toast.makeText(this, "Houve uma falha", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}