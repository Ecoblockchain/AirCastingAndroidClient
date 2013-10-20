package pl.llp.aircasting.activity;

import android.view.*;
import pl.llp.aircasting.Intents;
import pl.llp.aircasting.R;
import pl.llp.aircasting.activity.menu.MainMenu;
import pl.llp.aircasting.event.ui.TapEvent;
import pl.llp.aircasting.helper.LocationHelper;
import pl.llp.aircasting.helper.NoOp;
import pl.llp.aircasting.helper.SettingsHelper;
import pl.llp.aircasting.model.Session;
import pl.llp.aircasting.model.SessionManager;
import pl.llp.aircasting.receiver.SyncBroadcastReceiver;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.internal.Nullable;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

/**
 * A common superclass for activities that want to display left/right
 * navigation arrows
 */
public abstract class ButtonsActivity extends RoboMapActivityWithProgress implements View.OnClickListener {
    public static final String SHOW_BUTTONS = "showButtons";

    @Inject Context context;
    @Inject EventBus eventBus;

    // It seems it's impossible to inject these in the tests
    @InjectView(R.id.buttons) View buttons;

    @Nullable @InjectView(R.id.heat_map_button) View heatMapButton;
    @Nullable @InjectView(R.id.streams_button) View streamsButton;
    @Nullable @InjectView(R.id.graph_button) View graphButton;
    @Nullable @InjectView(R.id.trace_button) View traceButton;

    @InjectView(R.id.context_buttons) ViewGroup contextButtons;

    @Inject LocationManager locationManager;
    @Inject SessionManager sessionManager;
    @Inject LayoutInflater layoutInflater;
    @Inject LocationHelper locationHelper;
    @Inject SettingsHelper settingsHelper;
    @Inject MainMenu mainMenu;

    private boolean suppressTap = false;
    private boolean initialized = false;
    @Inject SyncBroadcastReceiver syncBroadcastReceiver;
    SyncBroadcastReceiver registeredReceiver;

    @Nullable @InjectView(R.id.zoom_in) Button zoomIn;
    @Nullable @InjectView(R.id.zoom_out) Button zoomOut;

    @Override
    protected void onResume() {
        super.onResume();

        initialize();
        locationHelper.start();

        updateButtons();

        if (!sessionManager.isSessionSaved()) {
            Intents.startSensors(context);
        }

        registerReceiver(syncBroadcastReceiver, SyncBroadcastReceiver.INTENT_FILTER);
        registeredReceiver = syncBroadcastReceiver;

        eventBus.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!sessionManager.isSessionSaved()) {
            Intents.stopSensors(context);
        }

        locationHelper.stop();

        if(registeredReceiver != null)
        {
          unregisterReceiver(syncBroadcastReceiver);
          registeredReceiver = null;
        }
        eventBus.unregister(this);
    }

    @Override
    protected boolean isRouteDisplayed() {
        // The maps server needs to know if we are displaying any routes
        return false;
    }

    private void initialize() {
        if (!initialized) {

            if (showButtons) {
                buttons.setVisibility(View.VISIBLE);
            } else {
                buttons.setVisibility(View.GONE);
            }

            if (graphButton != null) graphButton.setOnClickListener(this);
            if (traceButton != null) traceButton.setOnClickListener(this);
            if (heatMapButton != null) heatMapButton.setOnClickListener(this);
            if (streamsButton != null) streamsButton.setOnClickListener(this);

            initialized = true;
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        showButtons = savedInstanceState.getBoolean(SHOW_BUTTONS, true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SHOW_BUTTONS, showButtons);
    }

    protected boolean showButtons = true;

    /**
     * The next tap will not change the state of the toggleable
     * buttons. Use this if clicking something makes the toggleable
     * buttons toggle.
     */
    public void suppressNextTap() {
        suppressTap = true;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back_to_dashboard:
                finish();
                break;
            case R.id.graph_button:
                startActivity(new Intent(context, GraphActivity.class));
                break;
            case R.id.trace_button:
                startActivity(new Intent(context, SoundTraceActivity.class));
                break;
            case R.id.heat_map_button:
                startActivity(new Intent(context, HeatMapActivity.class));
                break;
            case R.id.streams_button:
                startActivity(new Intent(context, StreamsActivity.class));
                break;
            case R.id.toggle_aircasting:
                toggleAirCasting();
                break;
            case R.id.edit:
                Intents.editSession(this, sessionManager.getSession());
                break;
            case R.id.note:
                Intents.makeANote(this);
                break;
            case R.id.share:
                startActivity(new Intent(context, ShareSessionActivity.class));
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mainMenu.create(this, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mainMenu.handleClick(this, item);
    }

    @Subscribe
    public void onEvent(TapEvent event) {
        if (suppressTap) {
            suppressTap = false;
        }
    }

  protected void updateButtons()
  {

    clearButtons();
    if (sessionManager.isSessionStarted())
    {
      addButton(R.layout.context_button_stop);
      addButton(R.layout.context_button_note);
    }
    else if (sessionManager.isSessionSaved())
    {
      addButton(R.layout.context_button_edit);
      addButton(R.layout.context_button_share);
    }
    else
    {
      addButton(R.layout.context_button_record);
      addButton(R.layout.context_button_placeholder);
    }
    addContextSpecificButtons();
  }

    protected void addContextSpecificButtons() {
    }

    protected void clearButtons() {
        contextButtons.removeAllViews();
    }

    protected void addButton(int id) {
        View view = layoutInflater.inflate(id, contextButtons, false);
        view.setOnClickListener(this);
        contextButtons.addView(view);
    }

    private synchronized void toggleAirCasting() {
        if (sessionManager.isSessionStarted()) {
            stopAirCasting();
        } else {
            startAirCasting();
        }

        updateButtons();
    }

    private void stopAirCasting() {
      locationHelper.stop();
      Session session = sessionManager.getSession();
      Long sessionId = session.getId();
      if (session.isEmpty()) {
            Toast.makeText(context, R.string.no_data, Toast.LENGTH_SHORT).show();
            sessionManager.discardSession(sessionId);
        } else {
            sessionManager.stopSession();
            Intent intent = new Intent(this, SaveSessionActivity.class);
            intent.putExtra(Intents.SESSION_ID, sessionId);
            startActivityForResult(intent, Intents.SAVE_DIALOG);
        }
    }

  private void startAirCasting()
  {
    if (settingsHelper.areMapsDisabled())
    {
      sessionManager.startSession();
    }
    else
    {
      if (locationHelper.getLastLocation() == null)
      {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.cannot_record_session_due_to_location);
        builder.setNeutralButton(R.string.ok, NoOp.dialogOnClick()).show();
      }
      else
      {
        sessionManager.startSession();

        if (!settingsHelper.hasCredentials()) {
          Toast.makeText(context, R.string.account_reminder, Toast.LENGTH_LONG).show();
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
          if (!locationHelper.hasGPSFix())
          {
            Toast.makeText(context, R.string.no_gps_fix_warning, Toast.LENGTH_LONG).show();
          }
        }
        else
        {
          Toast.makeText(context, R.string.gps_off_warning, Toast.LENGTH_LONG).show();
        }
      }
    }
  }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Intents.SAVE_DIALOG:

                startActivity(new Intent(getApplicationContext(), SoundTraceActivity.class));
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
