package cz.septemberprojects.btbeatandroid;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Pair;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author MattyTesar
 */
public class BTBeatManager {
    
    public BluetoothAdapter bluetoothAdapter;    
    BroadcastReceiver BTReceiver;   
    
    private final String sharedPrefsID = "BTBeatPreferences";
    private final String applicationName;
    private final String version;  
    private ArrayList<Pair<String,String>> eventsDict = new ArrayList<Pair<String,String>>();
    
    private Context context;
    
    private boolean sendDataAutomatically = true;
    private long dataSubmitDelay = Long.MAX_VALUE;
    private Handler dataSubmitHandler;
    
    public boolean btunsupported = false;
    
    public boolean automaticEventsAllowed = true;
    
    
    private final String BTBEAT_HOST = "https://fromdingo.com/btbeat/api/";

    //list of fixed events
    
    
    public static final class Event {
        private static final String BTBEAT_EVENT_BT_UNSUPPORTED = "BT_UNSUPPORTED";
        public static String BTBEAT_EVENT_BT_UNAUTHORISED = "BT_NOT_AUTH";
        public static String BTBEAT_EVENT_BT_AUTHORISATION_REQUEST_SENT = "BT_AUTH_SENT";
        public static String BTBEAT_EVENT_BT_AUTHORISED = "BT_AUTH";
        public static String BTBEAT_EVENT_BT_TURNED_ON = "BT_ON";
        public static String BTBEAT_EVENT_BT_TURNED_OFF = "BT_OFF";
        public static String BTBEAT_EVENT_NOTIFICATION_SENT = "NF_SENT";
        public static String BTBEAT_EVENT_NOTIFICATION_CONVERSION = "NF_CONV";
    }

    
    public BTBeatManager(Context c, String appName, String appVersion) {
        context = c;
        applicationName = appName;
        version = appVersion;
        setupBTReceiver();
    }
    
    private void setupBTReceiver() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {       
            BTReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) && automaticEventsAllowed) {
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        Timestamp ts;
                        switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            addEvent(Event.BTBEAT_EVENT_BT_TURNED_OFF);
                            break;
                        case BluetoothAdapter.STATE_ON:
                            addEvent(Event.BTBEAT_EVENT_BT_TURNED_ON);
                            break;
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(BTReceiver, filter);
        } else {
            btunsupported = true;
            addEvent(Event.BTBEAT_EVENT_BT_UNSUPPORTED);
            try {
                createJSON();
            } catch (JSONException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void onResume() { 
        if(sendDataAutomatically) {
            try {
                createJSON();
            } catch (JSONException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }        
    }
    
    private void createJSON() throws JSONException, UnsupportedEncodingException {
        JSONObject completeJSON = new JSONObject();
        JSONArray eventsArray = new JSONArray();
        
        if(eventsDict.size() > 0) {
            for(int i = 0; i < eventsDict.size(); i++) {
                JSONObject event = new JSONObject();
                event.put("timestamp", eventsDict.get(i).first);
                event.put("event", eventsDict.get(i).second);
                eventsArray.put(event);
            }
        }
        completeJSON.put("events", eventsArray);
        
        SharedPreferences preferences = context.getSharedPreferences(sharedPrefsID, Context.MODE_PRIVATE);        
        boolean initialSent = preferences.getBoolean("initialSent", false);         
        if(!initialSent) {                        
            JSONObject initialData = new JSONObject();
            initialData.put("model", android.os.Build.DEVICE);
            initialData.put("locale", java.util.Locale.getDefault().toString());            
            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                initialData.put("ble_suppported", "false");
            } else {
                initialData.put("ble_suppported", "true");
            }
            completeJSON.put("initial-data", initialData);
        }
        
        completeJSON.put("application", applicationName);
        
        completeJSON.put("version", version);
        
        TelephonyManager tManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);        
        completeJSON.put("uuid", tManager.getDeviceId());
        
        if(eventsDict.size() > 0 || !initialSent) {            
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("initialSent", true);
            editor.commit();
            
            JSONObject finalJSON = new JSONObject();
            finalJSON.put("beat", completeJSON);
            sendJSON(finalJSON);
        }
    }
    
    
    private void sendJSON(final JSONObject jsonToSend) throws UnsupportedEncodingException {  

        int TIMEOUT_MILLISEC = 10000;  // = 10 seconds
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpClient client = getNewHttpClient();

        HttpPost request = new HttpPost(BTBEAT_HOST + "beats");
        StringEntity se = new StringEntity( jsonToSend.toString());  
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    request.setEntity(se);
        try {
            HttpResponse response = client.execute(request);
            if(response != null) {
                if(response.getStatusLine().getStatusCode() == 201) {            
                    eventsDict.clear();                   
                }                
            }
        } catch (IOException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public HttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception ex) {
            Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            return new DefaultHttpClient();
        }
    }
    
    public void setAutomaticDataSubmit() {
        sendDataAutomatically = true;
        if(sendDataAutomatically) {
            dataSubmitHandler.removeCallbacksAndMessages(null);
        }
    }
    
    
    public void allowAutomaticEvents(boolean allow) {
        automaticEventsAllowed = allow;
    }
    
    public void setSubmitDataPeriodicallyWithDelay(int delay) {
        sendDataAutomatically = false;
        dataSubmitDelay = delay;
        dataSubmitHandler = new Handler();
        dataSubmitHandler.postDelayed(dataSubmitThread, dataSubmitDelay);
    }
    
    private final Runnable dataSubmitThread = new Runnable()
    {
        public void run()
        {                
            try {
                createJSON();
            } catch (JSONException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
            }
            dataSubmitHandler.postDelayed(this, dataSubmitDelay);
        }
    };
    
    public void sendData() {
        try {
            createJSON();
        } catch (JSONException ex) {
            Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(BTBeatManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void addEvent(String eventName) {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        
        eventsDict.add(new Pair(ts.toString(), eventName));        
    }
}
