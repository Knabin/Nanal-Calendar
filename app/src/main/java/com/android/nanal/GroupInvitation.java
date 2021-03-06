package com.android.nanal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutionException;

import androidx.appcompat.app.AppCompatActivity;

public class GroupInvitation extends AppCompatActivity {
    TextView tvGroupName;
    Button btnJoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_invitation);

        tvGroupName = (TextView) findViewById(R.id.tvGroupName);
        btnJoin = (Button) findViewById(R.id.btnJoin);

        Intent intent = getIntent();
        final String groupId = intent.getStringExtra("groupId");

        tvGroupName.setText(groupId);

        btnJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String result = "fail";
                try {
                    final SharedPreferences loginPref = getSharedPreferences("login_setting", Context.MODE_PRIVATE);

                    String id = loginPref.getString("loginId", null);

                    GroupInvitationHelper groupInvitationHelper = new GroupInvitationHelper();
                    result = (String) groupInvitationHelper.execute(id, groupId).get();

                    if(result.equals("1")) {
                        // 그룹 가입 성공했을 경우
                        Toast.makeText(GroupInvitation.this, R.string.join_success, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        // 그룹 가입 실패했을 경우
                        Toast.makeText(GroupInvitation.this, R.string.join_fail, Toast.LENGTH_LONG).show();
                    }
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
