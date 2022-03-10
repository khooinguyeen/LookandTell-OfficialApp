package com.example.mainfinal;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        bottomNavigationView = findViewById(R.id.botNav);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment fragment = new Fragment();

                switch (item.getItemId()) {
                    case R.id.home:
                        fragment = new Home();
                        break;
                    case R.id.dichngu:
                        fragment = new Dichngu();
                        break;
                    case R.id.chuyenngu:
                        fragment = new Chuyenngu();
                        break;
                    case R.id.tudien:
                        fragment = new Tudien();
                        break;
                    default:break;
                }
                getSupportFragmentManager().beginTransaction().replace(R.id.main,fragment).commit();
                return true;
            }
        });
    }
}