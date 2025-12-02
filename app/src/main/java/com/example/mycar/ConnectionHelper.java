package com.example.mycar;
import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
public class ConnectionHelper {
    Connection con;
    String uname, pass, ip, port, database;
    @SuppressLint("NewApi")
    public Connection connectionclass()
    {
      ip = "192.168.0.180";
       database = "AutoSpendingDB";
       uname = "user3";
       pass = "123456";
        port = "1433";

        //для ноута
//        ip = "172.20.10.6";
//        database = "столовая";
//        uname = "user2";
//        pass = "user123456";
//        port = "1433";

//        ip = "91.105.176.43";
//       database = "AutoSpendingDB";
//       uname = "user3";
//       pass = "123456";
//        port = "8080";


        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Connection connection = null;
        String ConnectionURL = null;

        try
        {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            ConnectionURL = "jdbc:jtds:sqlserver://" + ip + ":" + port + ";" + "databasename=" +
                    database + ";user=" + uname + ";password=" + pass + ";";
            connection = DriverManager.getConnection(ConnectionURL);
        }
        catch (Exception ex){
            Log.e("Error ", ex.getMessage());
        }

        return connection;
    }



}
