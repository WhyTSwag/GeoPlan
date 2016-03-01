package fr.upem.geoplan;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.firebase.client.Firebase;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

import fr.upem.firecloud.FireCloudUser;
import fr.upem.geoplan.core.Event;
import fr.upem.geoplan.core.radar.RadarActivity;
import fr.upem.geoplan.core.User;
import fr.upem.geoplan.core.server.ServerApp;
import upem.fr.geoplan.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Firebase.setAndroidContext(this);
        ServerApp server = new ServerApp("https://blazing-inferno-2418.firebaseio.com/");
        ArrayList<FireCloudUser> guests = new ArrayList<>();


        FireCloudUser userTristan = server.createUser(1, "Tristan", "Fautrel", new LatLng(48.877535,2.59016), "0621185284");
        FireCloudUser userJeremie = server.createUser(2, "Jérémie", "Chattou", new LatLng(48.8385709, 2.561343), "0658596324");
        guests.add(userJeremie);
        guests.add(userTristan);
        server.createEvent(1, "tfautrel", "Rendez-vous Android", guests, null, null, "UPEM - Copernic", new LatLng(48.8392168, 2.5870625));


        Intent intent = new Intent(this, RadarActivity.class);
        intent.putExtra("event", new Event(new LatLng(10., 22.), "Event title"));
        ArrayList<User> users = new ArrayList<>();
        users.add(new User(1, "Pierre", "0678912345"));
        users.add(new User(2, "Maxime", "0033123456789"));
        intent.putExtra("users", users);

        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
