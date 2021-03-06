package com.android.nanal.group;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class CreateNewGroup extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... strings) {
        String str, sendMsg, receiveMsg = "";
        Log.d("CreateNewGroup", "doInBackground");
        try {
            URL url = new URL("http://ci2019nanal.dongyangmirae.kr/android/GroupCreate.jsp");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestMethod("POST");//데이터를 POST 방식으로 전송합니다.

            OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
            sendMsg = "group_name=" + strings[0] + "&group_color=" + strings[1] + "&user_id=" + strings[2];
            osw.write(sendMsg);
            osw.flush();
            osw.close();

            if (conn.getResponseCode() == conn.HTTP_OK) {
                InputStreamReader tmp = new InputStreamReader(conn.getInputStream(), "UTF-8");
                BufferedReader reader = new BufferedReader(tmp);
                StringBuffer buffer = new StringBuffer();
                while ((str = reader.readLine()) != null) {
                    buffer.append(str);
                }
                receiveMsg = buffer.toString();
                tmp.close();
                reader.close();
                Log.d("CreateNewGroup", "doInBackground 완료");
                Log.d("CreateNewGroup", receiveMsg);
            } else {
                Log.i("통신 결과", conn.getResponseCode() + "에러");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return receiveMsg;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);

    }
}
