package com.admin.mysdks;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

import com.admin.photosdk.TakePictureManager;

/**
 * 作者：create by comersss on 2019/3/27 17:06
 * 邮箱：904359289@qq.com
 */
public class MainActivity extends AppCompatActivity {

    private ImageView ivTest;
    private TakePictureManager takePictureManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ivTest = findViewById(R.id.iv_test);
        ivTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //显示在iv上
                takePictureManager = new TakePictureManager(MainActivity.this, "9", new TakePictureManager.takePictureCallBackListener() {
                    @Override
                    public void successful(Bitmap bitmap) {
                        ivTest.setImageBitmap(bitmap);//显示在iv上
                    }
                });
//                takePictureManager.setTailor(1, 1, 350, 350);
                takePictureManager.takePic();
            }
        });
    }

    //把本地的onActivityResult()方法回调绑定到对象
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == -1) {
            takePictureManager.attachToActivityForResult(requestCode, resultCode, data);
        }
    }

}
