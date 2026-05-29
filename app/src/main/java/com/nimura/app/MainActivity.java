package com.nimura.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.nimura.app.ui.HistoryFragment;
import com.nimura.app.ui.HomeFragment;
import com.nimura.app.ui.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (itemId == R.id.nav_history) {
                fragment = new HistoryFragment();
            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
            } else {
                return false;
            }
            loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
