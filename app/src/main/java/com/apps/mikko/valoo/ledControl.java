package com.apps.mikko.valoo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import android.os.Handler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class ledControl extends AppCompatActivity {

    Button btnOn, btnOff, btnDis;
    ListView respViewer;
    TextView lumn;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    Handler bluetoothIn;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    final int handlerState = 0;
    private ConnectedThread2 mConnectedThread;
    private String totalMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led_control);

        //receive the address of the bluetooth device
        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS);

        //view of the ledControl layout
        setContentView(R.layout.activity_led_control);
        //call the widgtes
        btnOn = (Button)findViewById(R.id.button2);
        btnOff = (Button)findViewById(R.id.button3);
        btnDis = (Button)findViewById(R.id.button4);
        respViewer = (ListView)findViewById(R.id.responseViewer);
        totalMessage = "";

        new ConnectBT().execute(); //Call the class to connect

        btnOn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                scan();
                if(null == mConnectedThread)
                {
                    mConnectedThread = new ConnectedThread2(btSocket);
                    mConnectedThread.start();
                }
            }
        });

        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                ArrayList nodesList = new ArrayList();
                displayNodes(nodesList);
                clear();
            }
        });

        btnDis.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });
        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                String readMessage = (String) msg.obj;
                totalMessage += readMessage;
                //msg(readMessage);
                if(totalMessage.startsWith("RESP:") && totalMessage.endsWith("#")) {
                    String trimmedMessage = totalMessage.replace("RESP:", "").replace("#","").trim();
                    handleResponse(trimmedMessage);
                    totalMessage = "";
                }
            }
        };
    }
    private void displayNodes(final ArrayList nodesList)
    {
        final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, nodesList);
        respViewer.setAdapter(adapter);
        respViewer.setOnItemClickListener(myListClickListener);
    }
    private void handleResponse(String readMessage)
    {
        String nodes[] = readMessage.split(";");
        ArrayList nodesList = new ArrayList();
        for (String node: nodes) {
            nodesList.add(node);
        }
        displayNodes(nodesList);
    }
    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick (AdapterView av, View v, int node_index, long arg3)
        {
            turnOnLed(node_index);
        }
    };
    private void checkBTState() {

        if(myBluetooth==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (myBluetooth.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(myUUID /*BTMODULEUUID*/);
        //creates secure outgoing connecetion with BT device using UUID
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout
    }
    private void sendMessage(final String message)
    {
        if (btSocket!=null)
        {
            try
            {
                final String endMarker = ":";
                final String messageToBeSent = message + endMarker;
                btSocket.getOutputStream().write(messageToBeSent.getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void clear()
    {
        sendMessage("CL");
    }
    private void scan()
    {
        sendMessage("SC");
    }

    private void turnOnLed(final int node_index)
    {
        final String message = "LED".toString() + Integer.toString(node_index);
        sendMessage(message);
    }
    private class ConnectedThread2 extends Thread {
        private InputStream mmInStream;
        public ConnectedThread2(BluetoothSocket socket) {
            InputStream tmpIn = null;
            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;

        }
        public void run() {
            while (true) {
                byte[] buffer = new byte[256];
                int bytes;

                // Keep looping to listen for received messages
                while (true) {
                    try {
                        bytes = mmInStream.read(buffer);        	//read bytes from input buffer
                        String readMessage = new String(buffer, 0, bytes);
                        // Send the obtained bytes to the UI Activity via handler
                        bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                    } catch (IOException e) {
                        break;
                    }
                }

            }
        }
    }
    private class ConnectedThread extends Thread {
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);        	//read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();

            }
        }

    }
    private class ResponseReaderBT extends AsyncTask<String, Void, String>
    {
        private InputStream mmInStream;
        @Override
        protected void onPreExecute()
        {
            InputStream tmpIn = null;
            try {
                //Create I/O streams for connection
                tmpIn = btSocket.getInputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
        }
        @Override
        protected String doInBackground(String... devices) //while the progress dialog is shown, the connection is done in background
        {
            byte[] buffer = new byte[256];
            int bytes = 0;
            String readMessage = "";
            while(true) {
                try
                {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    if (bytes > 0) {
                        readMessage = new String(buffer, 0, bytes);
                    }
                }
                catch(IOException e)
                {
                    break;
                }
            }
            return readMessage;
        }

        @Override
        protected void onPostExecute(String result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);
            msg(result);
        }
    }
    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(ledControl.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}
