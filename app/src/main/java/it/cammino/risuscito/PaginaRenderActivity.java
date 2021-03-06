package it.cammino.risuscito;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alertdialogpro.AlertDialogPro;
import com.alertdialogpro.ProgressDialogPro;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.cammino.utilities.material.LinearProgress;
import it.cammino.utilities.showcaseview.OnShowcaseEventListener;
import it.cammino.utilities.showcaseview.ShowcaseView;
import it.cammino.utilities.showcaseview.targets.ViewTarget;

public class PaginaRenderActivity extends ActionBarActivity {

    private DatabaseCanti listaCanti;
    private String pagina;
    private int idCanto;
    private static MediaPlayer mediaPlayer;
    private int favoriteFlag;
    private ImageButton favouriteCheckBox, play_scroll, rewind_button, play_button, ff_button, stop_button, save_file;
    public FloatingActionsMenu mFab; // the floating blue add/paste button
    DiscreteSeekBar scroll_speed_bar;
    private ProgressDialogPro mp3Dialog, exportDialog;
    private AlertDialogPro mProgressDialog;
    private PhoneStateListener phoneStateListener;
    private static OnAudioFocusChangeListener afChangeListener;
    private static AudioManager am;
    private String url;
    private int prevOrientation;
    private String primaNota;
    private String notaSalvata;
    public static String notaCambio;
    private String primoBarre;
    private String barreSalvato;
    private static String barreCambio;
    private String personalUrl;

    enum MP_State {
        Idle, Initialized, Prepared, Started, Paused,
        Stopped, PlaybackCompleted, End, Error, Preparing}

    static MP_State mediaPlayerState;

    //	private ProgressDialog mProgressDialog;
    private boolean localFile;
    private String localUrl;

    private WebView paginaView;
    private int defaultZoomLevel = 0;
    private int defaultScrollX = 0;
    private int defaultScrollY = 0;

    private static final String PREF_FIRST_OPEN = "prima_apertura_new";
    private static final String PREF_FIRST_OPEN_SCROLL = "prima_apertura_scroll";

    private Handler mHandler = new Handler();
    final Runnable mScrollDown = new Runnable()
    {
        public void run()
        {
            try {
                paginaView.scrollBy(0, Integer.valueOf(speedValue));
            }
            catch (NumberFormatException e) {
                paginaView.scrollBy(0, 0);
            }

            mHandler.postDelayed(this, SCROLL_SLEEP);
        }
    };
    public static String speedValue;
    private int savedSpeed;
    public static boolean scrollPlaying;
    private RelativeLayout.LayoutParams lps;

    private final long SCROLL_SLEEP = 700;

    private String localPDFPath;

    private static final int REQUEST_CODE = 6384;

    private LUtils mLUtils;

    public static boolean mostraAudio;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pagina_render);

        Toolbar toolbar = (Toolbar) findViewById(R.id.risuscito_toolbar);
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        setSupportActionBar(toolbar);

        // setta il colore della barra di stato, solo su KITKAT
        Utility.setupTransparentTints(PaginaRenderActivity.this);

        listaCanti = new DatabaseCanti(this);

        // recupera il numero della pagina da visualizzare dal parametro passato dalla chiamata
        Bundle bundle = this.getIntent().getExtras();
        pagina = bundle.getString("pagina");
        idCanto = bundle.getInt("idCanto");

        getRecordLink();

        paginaView = (WebView) findViewById(R.id.cantoView);

        try {
            primaNota = CambioAccordi.recuperaPrimoAccordo(getAssets().open(pagina + ".htm"));
            primoBarre = CambioAccordi.recuperaBarre(getAssets().open(pagina + ".htm"));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String query = "SELECT saved_tab, saved_barre, saved_speed" +
                "  FROM ELENCO" +
                "  WHERE _id =  " + idCanto;
        Cursor cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        notaSalvata = cursor.getString(0);
        barreSalvato = cursor.getString(1);
        savedSpeed = cursor.getInt(2);
        cursor.close();
        db.close();

        //recupera i pulsanti
        play_button = (ImageButton) findViewById(R.id.play_song);
        stop_button = (ImageButton) findViewById(R.id.stop_song);
        rewind_button = (ImageButton) findViewById(R.id.rewind_song);
        ff_button = (ImageButton) findViewById(R.id.fast_forward_song);
        save_file = (ImageButton) findViewById(R.id.save_file);
        play_scroll = (ImageButton) findViewById(R.id.play_scroll);
        scroll_speed_bar = (DiscreteSeekBar) findViewById(R.id.speed_seekbar);

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    // Lower the volume
                    if (mediaPlayerState == MP_State.Started) {
                        mediaPlayer.setVolume(0.1f, 0.1f);
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    // Raise it back to normal
                    if (mediaPlayerState == MP_State.Started) {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    }
                }
            }
        };

        stop_button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
//                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                cmdStop();
            }
        });

        // tenendo premuto il pulsante fast forward, si va avanti veloce
        ff_button.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {

                final Runnable r = new Runnable() {

                    public void run() {

                        int currentPosition = mediaPlayer.getCurrentPosition();
                        // check if seekForward time is lesser than song duration
                        if (currentPosition + 5000 <= mediaPlayer.getDuration()) {
                            // forward song
                            mediaPlayer.seekTo(currentPosition + 5000);
                        } else {
                            // forward to end position
                            mediaPlayer.seekTo(mediaPlayer.getDuration());
                        }

                        if(ff_button.isPressed()){
                            ff_button.postDelayed(this, 1000); //delayed for 1 sec
                        }else{

                            ff_button.postInvalidate();
                            ff_button.invalidate();
                        }
                    }
                };

                ff_button.post(r);

                return true;
            }
        });

        // tenendo premuto il pulsante rewind, si ravvolge
        rewind_button.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {

                final Runnable r = new Runnable() {

                    public void run() {

                        int currentPosition = mediaPlayer.getCurrentPosition();
                        // check if seekBackward time is greater than 0 sec
                        if (currentPosition - 5000 >= 0) {
                            // forward song
                            mediaPlayer.seekTo(currentPosition - 5000);
                        } else {
                            // backward to starting position
                            mediaPlayer.seekTo(0);
                        }

                        if(rewind_button.isPressed()){
                            rewind_button.postDelayed(this, 1000); //delayed for 1 sec
                        }else{

                            rewind_button.postInvalidate();
                            rewind_button.invalidate();
                        }
                    }
                };

                rewind_button.post(r);

                return true;
            }
        });

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    //Incoming call: Pause music
                    if (mediaPlayerState == MP_State.Started)
                        cmdPause();
                } else if(state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    //A call is dialing, active or on hold
                    if (mediaPlayerState == MP_State.Started)
                        cmdPause();
                }
                super.onCallStateChanged(state, incomingNumber);
            }
        };
        TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if(mgr != null) {
            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        if (!url.equalsIgnoreCase("")) {

            localUrl = Utility.retrieveMediaFileLink(this, url);

            if (localUrl.equalsIgnoreCase("") &&
                    personalUrl.equalsIgnoreCase(""))
                save_file.setSelected(false);
            else
                save_file.setSelected(true);

            //mostra i pulsanti per il lettore musicale
            play_button.setVisibility(View.VISIBLE);
            stop_button.setVisibility(View.VISIBLE);
            rewind_button.setVisibility(View.VISIBLE);
            ff_button.setVisibility(View.VISIBLE);

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayerState = MP_State.Idle;
                mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                if (localUrl.equalsIgnoreCase("")
                        && personalUrl.equalsIgnoreCase("")) {
                    localFile = false;
                    cmdSetDataSource(url);
                }
                else {
                    localFile = true;
                    if (!localUrl.equals(""))
                        cmdSetDataSource(localUrl);
                    else
                        cmdSetDataSource(personalUrl);
                }

                //disabilita il pulsante non utilizzabili in modalità stop
                disableButtonIcon(stop_button);
                disableButtonIcon(rewind_button);
                disableButtonIcon(ff_button);
            }
            else {
                switch (mediaPlayerState) {
                    case Started:
                        play_button.setSelected(true);
                        enableButtonIcon(stop_button);
                        enableButtonIcon(ff_button);
                        enableButtonIcon(rewind_button);
                        break;
                    case Paused:
                        play_button.setSelected(false);
                        enableButtonIcon(stop_button);
                        disableButtonIcon(ff_button);
                        disableButtonIcon(rewind_button);
                        break;
                    default:
                        play_button.setSelected(false);
                        disableButtonIcon(stop_button);
                        disableButtonIcon(ff_button);
                        disableButtonIcon(rewind_button);
                        break;
                }
            }


            // aggiunge il clicklistener sul pulsante play
            play_button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    //controlla la presenza di una connessione internet
                    if (!Utility.isOnline(PaginaRenderActivity.this)
                            && !localFile)  {
                        Toast.makeText(PaginaRenderActivity.this
                                , getString(R.string.no_connection), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    switch (mediaPlayerState) {
                        case Paused:
                            cmdStart();
                            break;
                        case Started:
                            cmdPause();
                            break;
                        case Initialized:
                            cmdPrepare();
                            break;
                        case Stopped:
                        case PlaybackCompleted:
                        default:
                            localUrl = Utility.retrieveMediaFileLink(getApplicationContext(), url);
                            if (localUrl.equalsIgnoreCase("")) {
                                if (personalUrl.equalsIgnoreCase("")) {
                                    localFile = false;
                                    cmdSetDataSource(url);
                                    save_file.setSelected(false);
                                }
                                else {
                                    localFile = true;
                                    cmdSetDataSource(personalUrl);
                                    save_file.setSelected(true);
                                }

                            }
                            else {
                                localFile = true;
                                cmdSetDataSource(localUrl);
                                save_file.setSelected(true);
                            }

                            if (mediaPlayerState == MP_State.Initialized)
                                cmdPrepare();
                            break;
                    }
                }
            });

            save_file.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.isSelected()) {
                        if (personalUrl.equalsIgnoreCase("")) {
                            prevOrientation = getRequestedOrientation();
                            Utility.blockOrientation(PaginaRenderActivity.this);
                            AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                            AlertDialogPro dialog = builder.setTitle(R.string.dialog_delete_mp3_title)
                                    .setMessage(R.string.dialog_delete_mp3)
                                    .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.DELETE_MP3_OK))
                                    .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS))
                                    .show();
                            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface arg0, int keyCode,
                                                     KeyEvent event) {
                                    if (keyCode == KeyEvent.KEYCODE_BACK
                                            && event.getAction() == KeyEvent.ACTION_UP) {
                                        arg0.dismiss();
                                        setRequestedOrientation(prevOrientation);
                                        return true;
                                    }
                                    return false;
                                }
                            });
                            dialog.setCancelable(false);
                        }
                        else {
                            prevOrientation = getRequestedOrientation();
                            Utility.blockOrientation(PaginaRenderActivity.this);
                            AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                            AlertDialogPro dialog = builder.setTitle(R.string.dialog_delete_link_title)
                                    .setMessage(R.string.dialog_delete_link)
                                    .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.DELETE_LINK_OK))
                                    .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS))
                                    .show();
                            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface arg0, int keyCode,
                                                     KeyEvent event) {
                                    if (keyCode == KeyEvent.KEYCODE_BACK
                                            && event.getAction() == KeyEvent.ACTION_UP) {
                                        arg0.dismiss();
                                        setRequestedOrientation(prevOrientation);
                                        return true;
                                    }
                                    return false;
                                }
                            });
                            dialog.setCancelable(false);
                        }
                    }
                    else {
                        prevOrientation = getRequestedOrientation();
                        Utility.blockOrientation(PaginaRenderActivity.this);
                        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                        AlertDialogPro dialog = builder.setTitle(R.string.download_link_title)
                                .setMessage(R.string.downlink_message)
                                .setPositiveButton(R.string.downlink_download, new ButtonClickedListener(Utility.DOWNLOAD_OK))
                                .setNegativeButton(R.string.downlink_choose, new ButtonClickedListener(Utility.DOWNLOAD_LINK))
                                .setNeutralButton(R.string.cancel, new ButtonClickedListener(Utility.DISMISS))
                                .show();
                        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface arg0, int keyCode,
                                                 KeyEvent event) {
                                if (keyCode == KeyEvent.KEYCODE_BACK
                                        && event.getAction() == KeyEvent.ACTION_UP) {
                                    arg0.dismiss();
                                    setRequestedOrientation(prevOrientation);
                                    return true;
                                }
                                return false;
                            }
                        });
                        dialog.setCancelable(false);
                    }
                }
            });

        }
        else {

            // aggiunge il clicklistener sul pulsante play
            play_button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    switch (mediaPlayerState) {
                        case Paused:
                            cmdStart();
                            break;
                        case Started:
                            cmdPause();
                            break;
                        case Initialized:
                            cmdPrepare();
                            break;
                        case Stopped:
                        case PlaybackCompleted:
                        default:
                            localFile = true;
                            cmdSetDataSource(personalUrl);
                            save_file.setSelected(true);

                            if (mediaPlayerState == MP_State.Initialized)
                                cmdPrepare();
                            break;
                    }
                }
            });

            save_file.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.isSelected()) {
                        prevOrientation = getRequestedOrientation();
                        Utility.blockOrientation(PaginaRenderActivity.this);
                        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                        AlertDialogPro dialog = builder.setTitle(R.string.dialog_delete_link_title)
                                .setMessage(R.string.dialog_delete_link)
                                .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.DELETE_ONLY_LINK_OK))
                                .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS))
                                .show();
                        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface arg0, int keyCode,
                                                 KeyEvent event) {
                                if (keyCode == KeyEvent.KEYCODE_BACK
                                        && event.getAction() == KeyEvent.ACTION_UP) {
                                    arg0.dismiss();
                                    setRequestedOrientation(prevOrientation);
                                    return true;
                                }
                                return false;
                            }
                        });
                        dialog.setCancelable(false);
                    }
                    else {
                        prevOrientation = getRequestedOrientation();
                        Utility.blockOrientation(PaginaRenderActivity.this);
                        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                        AlertDialogPro dialog = builder.setTitle(R.string.only_link_title)
                                .setMessage(R.string.only_link)
                                .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.DOWNLOAD_LINK))
                                .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS))
                                .show();
                        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface arg0, int keyCode,
                                                 KeyEvent event) {
                                if (keyCode == KeyEvent.KEYCODE_BACK
                                        && event.getAction() == KeyEvent.ACTION_UP) {
                                    arg0.dismiss();
                                    setRequestedOrientation(prevOrientation);
                                    return true;
                                }
                                return false;
                            }
                        });
                        dialog.setCancelable(false);
                    }
                }
            });

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayerState = MP_State.Idle;
                mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                //disabilita il pulsante non utilizzabili in modalità stop
                disableButtonIcon(stop_button);
                disableButtonIcon(rewind_button);
                disableButtonIcon(ff_button);
            }
            else {
                switch (mediaPlayerState) {
                    case Started:
                        play_button.setSelected(true);
                        enableButtonIcon(stop_button);
                        enableButtonIcon(ff_button);
                        enableButtonIcon(rewind_button);
                        break;
                    case Paused:
                        play_button.setSelected(false);
                        enableButtonIcon(stop_button);
                        disableButtonIcon(ff_button);
                        disableButtonIcon(rewind_button);
                        break;
                    default:
                        play_button.setSelected(false);
                        disableButtonIcon(stop_button);
                        disableButtonIcon(ff_button);
                        disableButtonIcon(rewind_button);
                        break;
                }
            }

            if (!personalUrl.equalsIgnoreCase("")) {
                save_file.setSelected(true);

                //mostra i pulsanti per il lettore musicale
                play_button.setVisibility(View.VISIBLE);
                stop_button.setVisibility(View.VISIBLE);
                rewind_button.setVisibility(View.VISIBLE);
                ff_button.setVisibility(View.VISIBLE);
            }
            else {
                // nasconde i pulsanti
                save_file.setSelected(false);
                play_button.setVisibility(View.GONE);
                stop_button.setVisibility(View.GONE);
                rewind_button.setVisibility(View.GONE);
                ff_button.setVisibility(View.GONE);

                final Runnable mMyRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PaginaRenderActivity.this
                                , getString(R.string.no_record), Toast.LENGTH_SHORT).show();
                    }
                };
                Handler myHandler = new Handler();
                myHandler.postDelayed(mMyRunnable, 1000);
            }

        }

        //converte il valore da 0 a 50  in un apercentual
        scroll_speed_bar.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
            @Override
            public int transform(int value) {
                return value * 2;
            }
        });
        scroll_speed_bar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                speedValue = String.valueOf(value);
            }
        });

        play_scroll.setSelected(false);

        play_scroll.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
//                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
//                play_scroll.setVisibility(View.GONE);
//                stop_scroll.setVisibility(View.VISIBLE);
                if (v.isSelected()) {
                    play_scroll.setSelected(false);
                    scrollPlaying = false;
                    mHandler.removeCallbacks(mScrollDown);
                }
                else {
                    play_scroll.setSelected(true);
                    scrollPlaying = true;
                    mScrollDown.run();
                }
            }
        });

        boolean showHelp1 = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(PREF_FIRST_OPEN, true);

        boolean showHelp2 = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(PREF_FIRST_OPEN_SCROLL, true);

        if(showHelp1) {
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(PaginaRenderActivity.this)
                    .edit();
            editor.putBoolean(PREF_FIRST_OPEN, false);
            editor.putBoolean(PREF_FIRST_OPEN_SCROLL, false);
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                editor.commit();
            } else {
                editor.apply();
            }

            final Runnable mMyRunnable = new Runnable() {
                @Override
                public void run() {
                    showHelp();
                }
            };
            Handler myHandler = new Handler();
            myHandler.postDelayed(mMyRunnable, 2000);
        }
        else {
            if (showHelp2){
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(PaginaRenderActivity.this)
                        .edit();
                editor.putBoolean(PREF_FIRST_OPEN_SCROLL, false);
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                    editor.commit();
                } else {
                    editor.apply();
                }
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(PaginaRenderActivity.this);
                showScrollHelp();
            }
        }

        initializeLoadingDialogs();

        AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
        mProgressDialog = builder.setTitle(R.string.download_running)
                .setView(getLayoutInflater().inflate(R.layout.dialog_load_determinate, null))
                .setPositiveButton(R.string.cancel, new ButtonClickedListener(Utility.DOWNLOAD_CANCEL)).create();
        mProgressDialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface arg0, int keyCode,
                                 KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    arg0.cancel();
                    return true;
                }
                return false;
            }
        });
        mProgressDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                setRequestedOrientation(prevOrientation);
            }
        });
        mProgressDialog.setCancelable(false);

        mLUtils = LUtils.getInstance(PaginaRenderActivity.this);
        ViewCompat.setTransitionName(findViewById(R.id.pagina_render_view), Utility.TRANS_PAGINA_RENDER);

        findViewById(R.id.fab_fullscreen_on).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getFab().toggle();
                mHandler.removeCallbacks(mScrollDown);
                saveZoom();
                Bundle bundle = new Bundle();
                bundle.putString(Utility.URL_CANTO, paginaView.getUrl());
                bundle.putInt(Utility.SPEED_VALUE, scroll_speed_bar.getProgress());
//                bundle.putInt(Utility.SPEED_VALUE, scroll_speed_bar.getValue());
                bundle.putBoolean(Utility.SCROLL_PLAYING, scrollPlaying);
                bundle.putInt(Utility.ID_CANTO, idCanto);

                Intent intent = new Intent(PaginaRenderActivity.this, PaginaRenderFullScreen.class);
                intent.putExtras(bundle);

                mLUtils.startActivityWithFadeIn(intent, paginaView, Utility.TAG_TRANSIZIONE);
            }
        });

        findViewById(R.id.fab_sound_off).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getFab().toggle();
                findViewById(R.id.music_controls).setVisibility(v.isSelected() ? View.VISIBLE : View.GONE);
                v.setSelected(!v.isSelected());
                mostraAudio = !v.isSelected();
            }
        });

        getFab().setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                showOuterFrame();
            }

            @Override
            public void onMenuCollapsed() {
                hideOuterFrame();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.canto, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (notaCambio == null || notaSalvata == null
                        || barreCambio == null || barreSalvato == null
                        || (notaCambio.equals(notaSalvata)
                        && barreCambio.equals(barreSalvato))) {
                    pulisciVars();
//                    finish();
                    mLUtils.closeActivityWithTransition();
                    return true;
                }
                else {
                    prevOrientation = getRequestedOrientation();
                    Utility.blockOrientation(PaginaRenderActivity.this);
                    AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                    AlertDialogPro dialog = builder.setTitle(R.string.dialog_save_tab_title)
                            .setMessage(R.string.dialog_save_tab)
                            .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.SAVE_TAB_OK))
                            .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS_EXIT))
                            .show();
                    dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode,
                                             KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK
                                    && event.getAction() == KeyEvent.ACTION_UP) {
                                arg0.dismiss();
                                setRequestedOrientation(prevOrientation);
                                return true;
                            }
                            return false;
                        }
                    });
                    dialog.setCancelable(false);
                    break;
                }
            case R.id.action_exp_pdf:
                (new PdfExportTask()).execute();
                return true;
            case R.id.action_help_canto:
                showHelp();
                return true;
            case R.id.action_save_tab:
                if (!notaSalvata.equalsIgnoreCase(notaCambio)) {
                    notaSalvata = notaCambio;
                    SQLiteDatabase db = listaCanti.getReadableDatabase();
                    String sql = "UPDATE ELENCO" +
                            "  SET saved_tab = \'" + notaCambio + "\' " +
                            "  WHERE _id =  " + idCanto;
                    db.execSQL(sql);
                    db.close();
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.tab_saved), Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.tab_not_saved), Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_reset_tab:
                notaCambio = primaNota;
                HashMap<String, String> convMap = CambioAccordi.diffSemiToni(primaNota, notaCambio);
                saveZoom();
                if (convMap != null) {
                    String nuovoFile = cambiaAccordi(convMap, barreCambio);
                    if (nuovoFile != null)
                        paginaView.loadUrl("file://" + nuovoFile);
                }
                else {
                    paginaView.loadUrl("file:///android_asset/" + pagina + ".htm");
                }
                if (defaultZoomLevel > 0)
                    paginaView.setInitialScale(defaultZoomLevel);
                paginaView.setWebViewClient(new MyWebViewClient());
                return true;
            case R.id.action_save_barre:
                if (!barreSalvato.equalsIgnoreCase(barreCambio)) {
                    barreSalvato = barreCambio;
                    SQLiteDatabase db = listaCanti.getReadableDatabase();
                    String sql = "UPDATE ELENCO" +
                            "  SET saved_barre = \'" + barreCambio + "\' " +
                            "  WHERE _id =  " + idCanto;
                    db.execSQL(sql);
                    db.close();
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.barre_saved), Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.barre_not_saved), Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_reset_barre:
                barreCambio = primoBarre;
                HashMap<String, String> convMap1 = CambioAccordi.diffSemiToni(primaNota, notaCambio);
                saveZoom();
                if (convMap1 != null) {
                    String nuovoFile = cambiaAccordi(convMap1, barreCambio);
                    if (nuovoFile != null)
                        paginaView.loadUrl("file://" + nuovoFile);
                }
                else {
                    paginaView.loadUrl("file:///android_asset/" + pagina + ".htm");
                }
                if (defaultZoomLevel > 0)
                    paginaView.setInitialScale(defaultZoomLevel);
                paginaView.setWebViewClient(new MyWebViewClient());
                return true;
            default:
                if (item.getGroupId() == R.id.menu_gruppo_note) {
                    notaCambio = String.valueOf(item.getTitleCondensed());
                    HashMap<String, String> convMap2 = CambioAccordi.diffSemiToni(primaNota, notaCambio);
                    saveZoom();
                    if (convMap2 != null) {
                        String nuovoFile = cambiaAccordi(convMap2, barreCambio);
                        if (nuovoFile != null)
                            paginaView.loadUrl("file://" + nuovoFile);
                    }
                    else {
                        paginaView.loadUrl("file:///android_asset/" + pagina + ".htm");
                    }
                    if (defaultZoomLevel > 0)
                        paginaView.setInitialScale(defaultZoomLevel);
                    paginaView.setWebViewClient(new MyWebViewClient());
                    return true;
                }
                if (item.getGroupId() == R.id.menu_gruppo_barre) {
                    barreCambio = String.valueOf(item.getTitleCondensed());
                    HashMap<String, String> convMap3 = CambioAccordi.diffSemiToni(primaNota, notaCambio);
                    saveZoom();
                    if (convMap3 != null) {
                        String nuovoFile = cambiaAccordi(convMap3, barreCambio);
                        if (nuovoFile != null)
                            paginaView.loadUrl("file://" + nuovoFile);
                    }
                    else {
                        paginaView.loadUrl("file:///android_asset/" + pagina + ".htm");
                    }
                    if (defaultZoomLevel > 0)
                        paginaView.setInitialScale(defaultZoomLevel);
                    paginaView.setWebViewClient(new MyWebViewClient());
                    return true;
                }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (notaCambio == null || notaSalvata == null
                    || barreCambio == null || barreSalvato == null
                    || (notaCambio.equals(notaSalvata)
                    && barreCambio.equals(barreSalvato))) {
                pulisciVars();
//                finish();
                mLUtils.closeActivityWithTransition();
                return true;
            }
            else {
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(PaginaRenderActivity.this);
                AlertDialogPro.Builder builder = new AlertDialogPro.Builder(PaginaRenderActivity.this);
                AlertDialogPro dialog = builder.setTitle(R.string.dialog_save_tab_title)
                        .setMessage(R.string.dialog_save_tab)
                        .setPositiveButton(R.string.confirm, new ButtonClickedListener(Utility.SAVE_TAB_OK))
                        .setNegativeButton(R.string.dismiss, new ButtonClickedListener(Utility.DISMISS_EXIT))
                        .show();
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            arg0.dismiss();
                            setRequestedOrientation(prevOrientation);
                            return true;
                        }
                        return false;
                    }
                });
                dialog.setCancelable(false);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onResume() {
        super.onResume();

        favouriteCheckBox = (ImageButton) findViewById(R.id.favorite);

//        favoriteFlag = selectFavouriteFromSource(pagina);
        favoriteFlag = selectFavouriteFromSource();

        if (favoriteFlag == 1)
            favouriteCheckBox.setColorFilter(getResources().getColor(R.color.favorite_accent_dark));
//            favouriteCheckBox.getDrawable().setColorFilter(getResources().getColor(R.color.favorite_accent), PorterDuff.Mode.SRC_ATOP);
//            favouriteCheckBox.getIconDrawable().setColorFilter(getResources().getColor(R.color.favorite_accent), PorterDuff.Mode.SRC_ATOP);
        else
            favouriteCheckBox.setColorFilter(getResources().getColor(android.R.color.white));
//            favouriteCheckBox.getDrawable().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
//            favouriteCheckBox.getIconDrawable().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);

        favouriteCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
//                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                if (favoriteFlag == 0) {
                    favoriteFlag = 1;
                    favouriteCheckBox.setColorFilter(getResources().getColor(R.color.favorite_accent_dark));
//                    favouriteCheckBox.getIconDrawable().setColorFilter(getResources().getColor(R.color.favorite_accent), PorterDuff.Mode.SRC_ATOP);
//                    favouriteCheckBox.getDrawable().setColorFilter(getResources().getColor(R.color.favorite_accent), PorterDuff.Mode.SRC_ATOP);
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.favorite_added), Toast.LENGTH_SHORT).show();
                }
                else {
                    favoriteFlag = 0;
//                    favouriteCheckBox.getIconDrawable().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
                    favouriteCheckBox.getDrawable().setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.favorite_removed), Toast.LENGTH_SHORT).show();
                }

                Bundle bundle = PaginaRenderActivity.this.getIntent().getExtras();
//                String pagina = bundle.getString("pagina");

//                updateFavouriteFlag(favoriteFlag, pagina);
                updateFavouriteFlag(favoriteFlag);
            }
        });

        checkScreenAwake();

        if (am != null && mediaPlayerState == MP_State.Started) {
            am.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }

        if (notaSalvata == null) {
            if (notaCambio == null) {
                notaSalvata = notaCambio = primaNota;
            }
            else {
                notaSalvata = primaNota;
            }
        }
        else {
//	    	Log.i("NOTA SALVATA", notaSalvata);
//	    	Log.i("AVVIO", "notaCambio = " + notaCambio);
            if (notaCambio == null) {
                notaCambio = notaSalvata;
            }
        }

        if (barreSalvato == null) {
            if (barreCambio == null) {
                barreSalvato = barreCambio = primoBarre;
            }
            else {
                barreSalvato = primoBarre;
            }
        }
        else {
//	    	Log.i("BARRESALVATO", barreSalvato);
            if (barreCambio == null) {
                barreCambio = barreSalvato;
            }
        }

        getZoom();

        //fix per crash su android 4.1
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
            paginaView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        HashMap<String, String> convMap = CambioAccordi.diffSemiToni(primaNota, notaCambio);
        if (convMap != null) {
            String nuovoFile = cambiaAccordi(convMap, barreCambio);
            if (nuovoFile != null)
                paginaView.loadUrl("file://" + nuovoFile);
        }
        else
            paginaView.loadUrl("file:///android_asset/" + pagina + ".htm");

        WebSettings webSettings = paginaView.getSettings();
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setLoadWithOverviewMode(true);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            webSettings.setBuiltInZoomControls(false);
        else {
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
        }

        if (defaultZoomLevel > 0)
            paginaView.setInitialScale(defaultZoomLevel);
        paginaView.setWebViewClient(new MyWebViewClient());

        if (speedValue == null) {
//	    	Log.i("SONO APPENA ENTRATO", "setto " + savedSpeed);
            scroll_speed_bar.setProgress(savedSpeed);
            speedValue = String.valueOf(scroll_speed_bar.getProgress());
//            scroll_speed_bar.setValue(savedSpeed);
//            speedValue = String.valueOf(scroll_speed_bar.getValue())
        }
        else {
//	    	Log.i("ROTAZIONE", "setto " + speedValue);
            scroll_speed_bar.setProgress(Integer.valueOf(speedValue));
//            scroll_speed_bar.setValue(Integer.valueOf(speedValue));
        }

//	    Log.i(this.getClass().toString(), "scrollPlaying? " + scrollPlaying);
        if (scrollPlaying) {
//            play_scroll.setVisibility(View.GONE);
//            stop_scroll.setVisibility(View.VISIBLE);
            play_scroll.setSelected(true);
            mScrollDown.run();
        }

        findViewById(R.id.music_controls).setVisibility(mostraAudio ? View.VISIBLE : View.GONE);
        findViewById(R.id.fab_sound_off).setSelected(!mostraAudio);
        if (getFab().isExpanded()) {
            View outerFrame = findViewById(R.id.outerFrame);
            outerFrame.setVisibility(View.VISIBLE);
            outerFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getFab().collapse();
                }
            });
        }

    }

    @Override
    public void onDestroy() {
        saveZoom();
        if (am != null)
            am.abandonAudioFocus(afChangeListener);
        if (listaCanti != null)
            listaCanti.close();
        super.onDestroy();
    }

    public FloatingActionsMenu getFab() {
        if (mFab == null)
            mFab = (FloatingActionsMenu) findViewById(R.id.fab_main_expand);
        return mFab;
    }

    private void showOuterFrame() {
        View outerFrame = findViewById(R.id.outerFrame);
        outerFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFab().collapse();
            }
        });

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            try {
//                final View container = findViewById(R.id.container);
//                int halfButtonHeight = getFab().getButton().getMeasuredHeight() / 2;
//                int fabMargin = (int) getResources().getDimension(R.dimen.floating_margin_lateral);
//                int actionbarMargin = (int)getResources().getDimension(R.dimen.abc_action_bar_default_height_material);
//                int cx = outerFrame.getMeasuredWidth() - fabMargin - halfButtonHeight;
//                int cy = outerFrame.getMeasuredHeight() - actionbarMargin - halfButtonHeight;
//
//                int finalRadius = Math.max(container.getWidth(), container.getHeight());
//                ViewAnimationUtils.createCircularReveal(outerFrame, cx, cy, 0, finalRadius).start();
//            } catch (IllegalStateException e) {
//                e.printStackTrace();
//            }
//            outerFrame.setVisibility(View.VISIBLE);
//
//        } else {
        outerFrame.setVisibility(View.VISIBLE);
//        }
    }

    private void hideOuterFrame() {
        final View outerFrame = findViewById(R.id.outerFrame);
        outerFrame.setOnClickListener(null);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            try {
//                final View container = findViewById(R.id.container);
//                int halfButtonHeight = getFab().getButton().getMeasuredHeight() / 2;
//                int fabMargin = (int) getResources().getDimension(R.dimen.floating_margin_lateral);
//                int actionbarMargin = (int)getResources().getDimension(R.dimen.abc_action_bar_default_height_material);
//                int cx = outerFrame.getMeasuredWidth() - fabMargin - halfButtonHeight;
//                int cy = outerFrame.getMeasuredHeight() - actionbarMargin - halfButtonHeight;
//
//                int finalRadius = Math.max(container.getWidth(), container.getHeight());
//                Animator anim = ViewAnimationUtils.createCircularReveal(outerFrame, cx, cy, finalRadius, 0);
//                anim.addListener(new AnimatorListenerAdapter() {
//                    @SuppressLint("NewApi")
//                    @Override
//                    public void onAnimationEnd(Animator animation) {
//                        super.onAnimationEnd(animation);
//                        outerFrame.setVisibility(View.GONE);
//                    }
//                });
//                anim.start();
//            } catch (IllegalStateException e) {
//                e.printStackTrace();
//                outerFrame.setVisibility(View.GONE);
//            }
//        } else {
        outerFrame.setVisibility(View.GONE);
//        }
    }

    public void pulisciVars() {
        saveZoom();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            mediaPlayerState = MP_State.Idle;
        }
        TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if(mgr != null)
            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);


        notaCambio = null;
        barreCambio = null;

        SaveSpeed();
        if (scrollPlaying) {
//            play_scroll.setVisibility(View.VISIBLE);
//            stop_scroll.setVisibility(View.GONE);
            play_scroll.setSelected(false);
            scrollPlaying = false;
            mHandler.removeCallbacks(mScrollDown);
        }
        speedValue = null;
        mostraAudio = false;
    }

    //controlla se l'app deve mantenere lo schermo acceso
    public void checkScreenAwake() {
        SharedPreferences pref =  PreferenceManager.getDefaultSharedPreferences(this);
        boolean screenOn = pref.getBoolean(Utility.SCREEN_ON, false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    //recupera il flag preferito per la pagina
    public int selectFavouriteFromSource() {

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String query = "SELECT favourite" +
                "  FROM ELENCO" +
                "  WHERE _id =  " + idCanto;
        Cursor cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        int favouriteFlag = cursor.getInt(0);

        cursor.close();
        db.close();
        return favouriteFlag;
    }

    //aggiorna il flag che indica se la pagina è tra i preferiti
    public void updateFavouriteFlag(int favouriteFlag) {

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String sql = "UPDATE ELENCO" +
                "  SET favourite = " + favouriteFlag + " " +
                "  WHERE _id =  " + idCanto;
        db.execSQL(sql);
        db.close();

    }

    //recupera e setta il record per la registrazione
    private void getRecordLink() {

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String query = "SELECT link" +
                "  FROM ELENCO" +
                "  WHERE _id =  " + idCanto;
        Cursor cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        if (cursor.getString(0) != null && !cursor.getString(0).equals(""))
            url = cursor.getString(0);
        else
            url = "";

        cursor.close();

        query = "SELECT local_path" +
                "  FROM LOCAL_LINKS" +
                "  WHERE _id =  " + idCanto;
        cursor = db.rawQuery(query, null);

        if (cursor.getCount() == 1) {
            cursor.moveToFirst();
            personalUrl = cursor.getString(0);
        }
        else
            personalUrl = "";

        cursor.close();
        db.close();

    }

    //recupera e setta lo zoom
    private void getZoom() {

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String query = "SELECT zoom, scroll_x , scroll_y" +
                "  FROM ELENCO" +
                "  WHERE _id =  " + idCanto;
        Cursor cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        defaultZoomLevel = cursor.getInt(0);
        defaultScrollX = cursor.getInt(1);
        defaultScrollY = cursor.getInt(2);

        cursor.close();
        db.close();

    }

    private void cmdSetDataSource(String path){
        if(mediaPlayerState == MP_State.Idle){
            try {
                if (!localFile)
                    mediaPlayer.setDataSource(path);
                else {
                    FileInputStream fileInputStream = new FileInputStream(path);
                    mediaPlayer.setDataSource(fileInputStream.getFD());
                    fileInputStream.close();
                }
                mediaPlayerState = MP_State.Initialized;
            } catch (IllegalArgumentException | IOException | IllegalStateException e) {
                Toast.makeText(PaginaRenderActivity.this,
                        e.toString(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }else{
            Toast.makeText(PaginaRenderActivity.this,
                    "Invalid State@cmdSetDataSource - skip",
                    Toast.LENGTH_SHORT).show();
        }

        if (mediaPlayerState != MP_State.Initialized)
            showMediaPlayerState();
    }

    private void cmdPrepare(){
        prevOrientation = getRequestedOrientation();
        Utility.blockOrientation(PaginaRenderActivity.this);
        mp3Dialog.show();
        mediaPlayer.setOnPreparedListener(mediaPlayerOnPreparedListener);
        mediaPlayer.setOnCompletionListener(mediaPlayerOnCompletedListener);

        if(mediaPlayerState == MP_State.Initialized
                ||mediaPlayerState == MP_State.Stopped
                || mediaPlayerState == MP_State.PlaybackCompleted){
            try {
                mediaPlayer.prepareAsync();
            } catch (IllegalStateException e) {
                Toast.makeText(PaginaRenderActivity.this,
                        e.toString(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }else{
            Toast.makeText(PaginaRenderActivity.this,
                    "Invalid State@cmdPrepare() - skip",
                    Toast.LENGTH_SHORT).show();
        }

        if (mediaPlayerState != MP_State.Prepared
                && mediaPlayerState != MP_State.Initialized)
            showMediaPlayerState();
    }

    private void cmdStart(){
        if(mediaPlayerState == MP_State.Prepared
                ||mediaPlayerState == MP_State.Started
                ||mediaPlayerState == MP_State.Paused
                ||mediaPlayerState == MP_State.PlaybackCompleted){

            //gestisce l'abbassamento del volume in caso di altre riproduzioni (sms, etc.)
            int result = am.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                play_button.setSelected(true);
                enableButtonIcon(stop_button);
                enableButtonIcon(ff_button);
                enableButtonIcon(rewind_button);
                mediaPlayerState = MP_State.Started;
            }
            else {
                Toast.makeText(PaginaRenderActivity.this,
                        "AudioFocus non consentito",
                        Toast.LENGTH_SHORT).show();
            }

        }else{
            Toast.makeText(PaginaRenderActivity.this,
                    "Invalid State@cmdStart() - skip",
                    Toast.LENGTH_SHORT).show();
        }
        showMediaPlayerState();
    }

    private void cmdPause(){
        if(mediaPlayerState == MP_State.Started
                ||mediaPlayerState == MP_State.Paused){
            mediaPlayer.pause();
            am.abandonAudioFocus(afChangeListener);
            play_button.setSelected(false);
            enableButtonIcon(stop_button);
            disableButtonIcon(ff_button);
            disableButtonIcon(rewind_button);
            mediaPlayerState = MP_State.Paused;
        }else{
            Toast.makeText(PaginaRenderActivity.this,
                    "Invalid State@cmdPause() - skip",
                    Toast.LENGTH_SHORT).show();
        }
        showMediaPlayerState();
    }

    private void cmdStop(){
        if(mediaPlayerState == MP_State.Started
                ||mediaPlayerState == MP_State.Paused) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            am.abandonAudioFocus(afChangeListener);
            play_button.setSelected(false);
            disableButtonIcon(stop_button);
            disableButtonIcon(ff_button);
            disableButtonIcon(rewind_button);
            mediaPlayerState = MP_State.Stopped;
            showMediaPlayerState();
            mediaPlayerState = MP_State.Idle;
        }else{
            Toast.makeText(PaginaRenderActivity.this,
                    "Invalid State@cmdStop() - skip",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showMediaPlayerState(){

        String state = "";

        switch(mediaPlayerState){
            case Idle:
                state = "Idle";
                break;
            case Initialized:
                state = "Initialized";
                break;
            case Prepared:
                state = "Prepared";
                break;
            case Started:
                state = "Started";
                break;
            case Paused:
                state = "Paused";
                break;
            case Stopped:
                state = "Stopped";
                break;
            case PlaybackCompleted:
                state = "PlaybackCompleted";
                break;
            case End:
                state = "End";
                break;
            case Error:
                state = "Error";
                break;
            case Preparing:
                state = "Preparing";
                break;
            default:
                state = "Unknown!";
        }

        Toast.makeText(PaginaRenderActivity.this
                , "Stato del lettore: " + state, Toast.LENGTH_SHORT).show();
    }

    OnErrorListener mediaPlayerOnErrorListener
            = new OnErrorListener(){

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            try {
                if (mp3Dialog.isShowing())
                    mp3Dialog.dismiss();
            }
            catch (IllegalArgumentException e) {}
            mediaPlayerState = MP_State.Error;
            showMediaPlayerState();
            return false;
        }
    };

    OnPreparedListener mediaPlayerOnPreparedListener
            =  new OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mp) {
            try {
                if (mp3Dialog.isShowing())
                    mp3Dialog.dismiss();
            }
            catch (IllegalArgumentException e) {}
            mediaPlayerState = MP_State.Prepared;
            cmdStart();
        }
    };

    OnCompletionListener mediaPlayerOnCompletedListener
            =  new OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {
            cmdStop();
            mediaPlayerState = MP_State.PlaybackCompleted;
            showMediaPlayerState();
            mediaPlayerState = MP_State.Idle;
        }
    };

    private class ButtonClickedListener implements DialogInterface.OnClickListener {
        private int clickedCode;

        public ButtonClickedListener(int code) {
            clickedCode = code;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (clickedCode) {
                case Utility.DISMISS:
                    setRequestedOrientation(prevOrientation);
                    break;
                case Utility.DISMISS_EXIT:
                    pulisciVars();
//                    finish();
                    mLUtils.closeActivityWithTransition();
                    break;
                case Utility.DOWNLOAD_CANCEL:
                    mProgressDialog.cancel();
                    break;
                case Utility.DOWNLOAD_OK:
                    final DownloadTask downloadTask = new DownloadTask(PaginaRenderActivity.this);
                    SharedPreferences pref =  PreferenceManager.getDefaultSharedPreferences(PaginaRenderActivity.this);
                    int saveLocation = pref.getInt(Utility.SAVE_LOCATION, 0);
                    if (saveLocation == 1) {
                        if (Utility.isExternalStorageWritable()) {
//                            File[] fileArray = ContextCompat.getExternalFilesDirs(PaginaRenderActivity.this, null);
//                            String address = fileArray[0].getAbsolutePath();
//                            int replaceIndex = address.indexOf("Android/data");
//                        Log.i(getClass().toString(), "INDIRIZZO OLD: " + address);
//                        Log.i(getClass().toString(), "INDICE ANDROID/DATA: " + replaceIndex);
//                            if (replaceIndex > 0) {
//                                address = address.replace(address.substring(replaceIndex), "risuscito");
//                            }

//                            Log.i(getClass().toString(), "INDIRIZZO: " + address);
//                        String localFile = fileArray[0].getAbsolutePath()
                            boolean folderCreated = new File(Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_MUSIC), "Risuscitò").mkdirs();
//                            Log.i(getClass().toString(), "RISUSCITO CREATA: " + folderCreated);
                            String localFile = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_MUSIC).getAbsolutePath()
                                    + "/Risuscitò/" + Utility.filterMediaLinkNew(url);
//                            Log.i(getClass().toString(), "LOCAL FILE: " + localFile);
                            downloadTask.execute(url, localFile);
                        }
                        else
                            Toast.makeText(PaginaRenderActivity.this
                                    , getString(R.string.no_memory_writable), Toast.LENGTH_SHORT).show();
                    }
                    else {
                        String localFile = PaginaRenderActivity.this.getFilesDir()
                                + "/"
                                + Utility.filterMediaLink(url);
                        downloadTask.execute(url, localFile);
                    }

                    mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            Toast.makeText(PaginaRenderActivity.this, getString(R.string.download_cancelled), Toast.LENGTH_SHORT).show();
                            downloadTask.cancel(true);
                            setRequestedOrientation(prevOrientation);
                        }
                    });
                    break;
                case Utility.DOWNLOAD_LINK:
                    setRequestedOrientation(prevOrientation);
                    // This always works
                    Intent i = new Intent(getApplicationContext(), FilePickerActivity.class);
                    //This works if you defined the intent filter
//                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                    // Set these depending on your use case. These are the defaults.
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
                    i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                    i.putExtra(FilePickerActivity.BACKGROUND_COLOR, R.color.theme_primary);

                    startActivityForResult(i, REQUEST_CODE);
//                    startActivityForResult(new Intent(
//                            PaginaRenderActivity.this, FileChooserActivity.class), REQUEST_CODE);
                    break;
                case Utility.DELETE_MP3_OK:
                    File fileToDelete = new File(localUrl);
                    fileToDelete.delete();
                    if (fileToDelete.getAbsolutePath().contains("/Risuscit")) {
                        // initiate media scan and put the new things into the path array to
                        // make the scanner aware of the location and the files you want to see
                        MediaScannerConnection.scanFile(getApplicationContext()
                                , new String[] {fileToDelete.getAbsolutePath()}
                                , null
                                , null);
                    }
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.file_delete)
                            , Toast.LENGTH_SHORT).show();

                    if (mediaPlayerState == MP_State.Started
                            || mediaPlayerState == MP_State.Paused)
                        cmdStop();

                    mediaPlayer = new MediaPlayer();
                    mediaPlayerState = MP_State.Idle;
                    mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                    localFile = false;
                    cmdSetDataSource(url);
                    save_file.setSelected(false);
//                    enableButtonIcon(save_file);
//                    save_file.setVisibility(View.VISIBLE);
//                    disableButtonIcon(delete_file);
//                    delete_file.setVisibility(View.GONE);
                    setRequestedOrientation(prevOrientation);
                    break;
                case Utility.DELETE_LINK_OK:
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.delink_delete)
                            , Toast.LENGTH_SHORT).show();

                    if (mediaPlayerState == MP_State.Started
                            || mediaPlayerState == MP_State.Paused)
                        cmdStop();

                    mediaPlayer = new MediaPlayer();
                    mediaPlayerState = MP_State.Idle;
                    mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                    localFile = false;
                    personalUrl = "";

                    SQLiteDatabase db = listaCanti.getReadableDatabase();
                    String sql = "DELETE FROM LOCAL_LINKS" +
                            "  WHERE _id =  " + idCanto;
                    db.execSQL(sql);
                    db.close();

                    save_file.setSelected(false);
//                    enableButtonIcon(save_file);
//                    save_file.setVisibility(View.VISIBLE);
//                    disableButtonIcon(delete_file);
//                    delete_file.setVisibility(View.GONE);

                    setRequestedOrientation(prevOrientation);
                    break;
                case Utility.DELETE_ONLY_LINK_OK:
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.delink_delete)
                            , Toast.LENGTH_SHORT).show();

                    if (mediaPlayerState == MP_State.Started
                            || mediaPlayerState == MP_State.Paused)
                        cmdStop();

                    mediaPlayer = new MediaPlayer();
                    mediaPlayerState = MP_State.Idle;
                    mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                    localFile = false;
                    personalUrl = "";

                    db = listaCanti.getReadableDatabase();
                    sql = "DELETE FROM LOCAL_LINKS" +
                            "  WHERE _id =  " + idCanto;
                    db.execSQL(sql);
                    db.close();

                    save_file.setSelected(false);
//                    enableButtonIcon(save_file);
//                    save_file.setVisibility(View.VISIBLE);
//                    disableButtonIcon(delete_file);
//                    delete_file.setVisibility(View.GONE);

                    play_button.setVisibility(View.GONE);
                    stop_button.setVisibility(View.GONE);
                    rewind_button.setVisibility(View.GONE);
                    ff_button.setVisibility(View.GONE);

                    setRequestedOrientation(prevOrientation);
                    break;
                case Utility.SAVE_TAB_OK:
                    db = listaCanti.getReadableDatabase();
                    sql = "UPDATE ELENCO" +
                            "  SET saved_tab = \'" + notaCambio + "\' " +
                            "    , saved_barre = \'" + barreCambio + "\' " +
                            "  WHERE _id =  " + idCanto;
                    db.execSQL(sql);
                    db.close();
                    pulisciVars();
//                    finish();
                    mLUtils.closeActivityWithTransition();
                    break;
                default:
                    setRequestedOrientation(prevOrientation);
                    break;
            }
        }
    }

    private void saveZoom(){
        defaultZoomLevel = (int) (paginaView.getScale() *100);
        defaultScrollX = paginaView.getScrollX();
        defaultScrollY = paginaView.getScrollY();

        SQLiteDatabase db = listaCanti.getReadableDatabase();

        String sql = "UPDATE ELENCO" +
                "  SET zoom = " + defaultZoomLevel + " " +
                ", scroll_x = " + defaultScrollX + " " +
                ", scroll_y = " + defaultScrollY + " " +
                "  WHERE _id =  " + idCanto;
        db.execSQL(sql);
        db.close();
    }

    private void SaveSpeed(){
        SQLiteDatabase db = listaCanti.getReadableDatabase();
        String sql = "UPDATE ELENCO" +
                "  SET saved_speed = " + speedValue +
                "  WHERE _id =  " + idCanto;
        db.execSQL(sql);
        db.close();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE:
                // If the file selection was successful
//                if (resultCode == RESULT_OK) {
                if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
//                        Log.i(FILE_CHOOSER_TAG, "Uri = " + uri.toString());
                        try {
                            // Get the file path from the URI
//                            String path = FileUtils.getPath(this, uri);
                            String path = uri.getPath();
                            Toast.makeText(PaginaRenderActivity.this,
                                    getResources().getString(R.string.file_selected)
                                            + ": "
                                            + path, Toast.LENGTH_LONG).show();

                            if (mediaPlayerState == MP_State.Started
                                    || mediaPlayerState == MP_State.Paused)
                                cmdStop();
                            mediaPlayer = new MediaPlayer();
                            mediaPlayerState = MP_State.Idle;
                            mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                            SQLiteDatabase db = listaCanti.getReadableDatabase();
                            ContentValues values = new ContentValues();
                            values.put("_id", idCanto);
                            values.put("local_path", path);
                            db.insert("LOCAL_LINKS", null, values);
                            db.close();

                            localFile = true;
                            personalUrl = path;

                            save_file.setSelected(true);
//                            disableButtonIcon(save_file);
//                            save_file.setVisibility(View.GONE);
//                            enableButtonIcon(delete_file);
//                            delete_file.setVisibility(View.VISIBLE);

                            //mostra i pulsanti per il lettore musicale
                            play_button.setVisibility(View.VISIBLE);
                            stop_button.setVisibility(View.VISIBLE);
                            rewind_button.setVisibility(View.VISIBLE);
                            ff_button.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            Log.e("FileSelectorTestActivity", "File select error", e);
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (defaultScrollX > 0 || defaultScrollY > 0)
                        paginaView.scrollTo(defaultScrollX, defaultScrollY);
                }
                // Delay the scrollTo to make it work
            }, 600);
            super.onPageFinished(view, url);
        }
    }

    private String cambiaAccordi(HashMap<String, String> conversione, String barre) {
        String cantoTrasportato = this.getFilesDir() + "/temporaneo.htm";

        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            getAssets().open(pagina + ".htm"), "UTF-8"));

            String line = br.readLine();

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(cantoTrasportato), "UTF-8"));

            Pattern pattern = Pattern.compile("Do#|Do|Re|Mib|Mi|Fa#|Fa|Sol#|Sol|La|Sib|Si");
            while (line != null) {
                if (line.contains("A13F3C") && !line.contains("<H2>") && !line.contains("<H4>")) {
//	        		Log.i("RIGA", line);
                    Matcher matcher = pattern.matcher(line);
                    StringBuffer sb = new StringBuffer();
                    while(matcher.find()) {
                        matcher.appendReplacement(sb, conversione.get(matcher.group(0)));
                    }
                    matcher.appendTail(sb);
                    out.write(sb.toString());
                    out.newLine();
                }
                else {
                    if (line.contains("<H3>")) {
                        if (barre != null && !barre.equals("0")) {
                            String oldLine = "<H4><FONT COLOR=\"#A13F3C\"><I>Barrè al " + barre +  " tasto</I></FONT></H4>";
                            out.write(oldLine);
                            out.newLine();
                        }
                        out.write(line);
                        out.newLine();
                    }
                    else {
                        if (!line.contains("Barrè") && !line.contains("Barr&#232;")) {
                            out.write(line);
                            out.newLine();
                        }
                    }
                }
                line = br.readLine();
            }
            br.close();
            out.flush();
            out.close();
            return cantoTrasportato;
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showHelp() {
        prevOrientation = getRequestedOrientation();
        Utility.blockOrientation(PaginaRenderActivity.this);
        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                new ViewTarget(R.id.tonalita, PaginaRenderActivity.this)
                , PaginaRenderActivity.this
                , R.string.action_tonalita
                , R.string.showcase_tonalita_desc);
        showCase.setButtonText(getString(R.string.showcase_button_next));
        showCase.setScaleMultiplier(0.3f);
        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                        new ViewTarget(R.id.tonalita, PaginaRenderActivity.this)
                        , PaginaRenderActivity.this
                        , "1) " + getString(R.string.action_trasporta)
                        , getString(R.string.showcase_chtab_desc));
                showCase.setButtonText(getString(R.string.showcase_button_next));
                showCase.setScaleMultiplier(0.3f);
                showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                    @Override
                    public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                    @Override
                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                new ViewTarget(R.id.tonalita, PaginaRenderActivity.this)
                                , PaginaRenderActivity.this
                                , "2) " + getString(R.string.action_salva_tonalita)
                                , getString(R.string.showcase_savetab_desc));
                        showCase.setButtonText(getString(R.string.showcase_button_next));
                        showCase.setScaleMultiplier(0.3f);
                        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                            @Override
                            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                            @Override
                            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                        new ViewTarget(R.id.tonalita, PaginaRenderActivity.this)
                                        , PaginaRenderActivity.this
                                        , "3) " + getString(R.string.action_reset_tonalita)
                                        , getString(R.string.showcase_restab_desc));
                                showCase.setButtonText(getString(R.string.showcase_button_next));
                                showCase.setScaleMultiplier(0.3f);
                                showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                                    @Override
                                    public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                                    @Override
                                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                                new ViewTarget(R.id.barre, PaginaRenderActivity.this)
                                                , PaginaRenderActivity.this
                                                , R.string.action_barre
                                                , R.string.showcase_barre_desc);
                                        showCase.setButtonText(getString(R.string.showcase_button_next));
                                        showCase.setScaleMultiplier(0.3f);
                                        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                                            @Override
                                            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                                            @Override
                                            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                                ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                                        new ViewTarget(R.id.barre, PaginaRenderActivity.this)
                                                        , PaginaRenderActivity.this
                                                        , "1) " + getString(R.string.action_trasporta)
                                                        , getString(R.string.showcase_chbarre_desc));
                                                showCase.setButtonText(getString(R.string.showcase_button_next));
                                                showCase.setScaleMultiplier(0.3f);
                                                showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                                                    @Override
                                                    public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                                                    @Override
                                                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                                        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                                                new ViewTarget(R.id.barre, PaginaRenderActivity.this)
                                                                , PaginaRenderActivity.this
                                                                , "2) " + getString(R.string.action_salva_tonalita)
                                                                , getString(R.string.showcase_savebarre_desc));
                                                        showCase.setButtonText(getString(R.string.showcase_button_next));
                                                        showCase.setScaleMultiplier(0.3f);
                                                        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                                                            @Override
                                                            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                                                            @Override
                                                            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                                                ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                                                        new ViewTarget(R.id.barre, PaginaRenderActivity.this)
                                                                        , PaginaRenderActivity.this
                                                                        , "3) " + getString(R.string.action_reset_barre)
                                                                        , getString(R.string.showcase_resbarre_desc));
                                                                showCase.setScaleMultiplier(0.3f);
                                                                showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                                                                    @Override
                                                                    public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                                                                    @Override
                                                                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                                                        showScrollHelp();
                                                                    }

                                                                    @Override
                                                                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                                                                });
                                                            }

                                                            @Override
                                                            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                                                        });
                                                    }

                                                    @Override
                                                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                                                });
                                            }

                                            @Override
                                            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                                        });
                                    }

                                    @Override
                                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                                });
                            }

                            @Override
                            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                        });
                    }

                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                });

            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
        });
    }

    public void showScrollHelp() {
        lps = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lps.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        int marginTop = ((Number) ( getApplicationContext().getResources().getDisplayMetrics().density * 40)).intValue();
        int marginRight = ((Number) ( getApplicationContext().getResources().getDisplayMetrics().density * 12)).intValue();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            marginRight = ((Number) ( getApplicationContext().getResources().getDisplayMetrics().density * 62)).intValue();
        }
        lps.setMargins(marginTop, marginTop, marginRight, marginTop);

        ShowcaseView.ConfigOptions co = new ShowcaseView.ConfigOptions();
        co.buttonLayoutParams = lps;

        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                new ViewTarget(R.id.play_scroll, PaginaRenderActivity.this)
                , PaginaRenderActivity.this
                , R.string.play_scroll
                , R.string.showcase_scroll_desc
                , co);
        showCase.setButtonText(getString(R.string.showcase_button_next));
        showCase.setScaleMultiplier(0.3f);
        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                ShowcaseView.ConfigOptions co = new ShowcaseView.ConfigOptions();
                co.buttonLayoutParams = lps;
                ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                        new ViewTarget(R.id.speed_seekbar, PaginaRenderActivity.this)
                        , PaginaRenderActivity.this
                        , R.string.scroll_seekbar
                        , R.string.showcase_seekbar_desc
                        , co);
                showCase.setButtonText(getString(R.string.showcase_button_next));
                showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                    @Override
                    public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                    @Override
                    public void onShowcaseViewHide(ShowcaseView showcaseView) {
                        ShowcaseView showCase = ShowcaseView.insertShowcaseView(
                                new ViewTarget(R.id.speed_seekbar, PaginaRenderActivity.this)
                                , PaginaRenderActivity.this
                                , R.string.showcase_end_title
                                , R.string.showcase_help_general);
                        showCase.setShowcase(ShowcaseView.NONE);
                        showCase.setOnShowcaseEventListener(new OnShowcaseEventListener() {

                            @Override
                            public void onShowcaseViewShow(ShowcaseView showcaseView) { }

                            @Override
                            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                                setRequestedOrientation(prevOrientation);
                            }

                            @Override
                            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                        });
                    }

                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
                });
            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) { }
        });
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;

        public DownloadTask(Context context) {
            this.context = context;
        }

        @SuppressLint("Wakelock")
        @Override
        protected String doInBackground(String... sUrl) {
            // take CPU lock to prevent CPU from going off if the user 
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            wakelock.acquire();

            try {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(sUrl[0]);
//                    Log.i(PaginaRenderActivity.this.getClass().toString(), "URL[0]:" + sUrl[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report 
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                        return "Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage();

                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();
                    output = new FileOutputStream(sUrl[1]);
//                    Log.i(PaginaRenderActivity.this.getClass().toString(), "URL[1]:" + sUrl[1]);

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        if (isCancelled()) {
                            try {
                                if (output != null)
                                    output.close();
                                if (input != null)
                                    input.close();
                                File fileToDelete = new File(sUrl[1]);
                                fileToDelete.delete();
                            }
                            catch (IOException ignored) { }
                            if (connection != null)
                                connection.disconnect();
                            return null;
                        }
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) // only if total length is known
                            publishProgress((int) (total * 100 / fileLength));
                        output.write(data, 0, count);
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    }
                    catch (IOException ignored) { }

                    if (connection != null)
                        connection.disconnect();
                }
            } finally {
                wakelock.release();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
//            ((ProgressBarIndeterminateDeterminate) mProgressDialog.findViewById(R.id.progressDeterminate)).setProgress(progress[0]);
            ((LinearProgress) mProgressDialog.findViewById(R.id.progressDeterminate)).setProgress(progress[0]);
            if (progress[0] != 0)
                ((TextView) mProgressDialog.findViewById(R.id.percent_text))
                        .setText(progress[0].toString() + " %");
        }

        @Override
        protected void onPostExecute(String result) {
            if (mProgressDialog.isShowing())
                mProgressDialog.dismiss();
            if (result != null)
                Toast.makeText(context,"Errore nel download: " + result, Toast.LENGTH_LONG).show();
            else {
                SharedPreferences pref =  PreferenceManager.getDefaultSharedPreferences(PaginaRenderActivity.this);
                int saveLocation = pref.getInt(Utility.SAVE_LOCATION, 0);
                if (saveLocation == 1) {
//                    File[] fList = new File(Environment.getExternalStoragePublicDirectory(
//                            Environment.DIRECTORY_MUSIC).getAbsolutePath()
//                            + "/Risuscitò/").listFiles();
//                    String[] filePaths = new String[fList.length];
//                    // get all the files from a directory
//                    for (int i = 0; i < fList.length; i++) {
//                        filePaths[i] = fList[i].getAbsolutePath();
//                    }
                    // initiate media scan and put the new things into the path array to
                    // make the scanner aware of the location and the files you want to see
                    MediaScannerConnection.scanFile(context
                            , new String[] {Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC).getAbsolutePath()
                            + "/Risuscitò/" + Utility.filterMediaLinkNew(url)}
                            , null
                            , null);
                }
                Toast.makeText(context, getString(R.string.download_completed), Toast.LENGTH_SHORT).show();

                if (mediaPlayerState == MP_State.Started
                        || mediaPlayerState == MP_State.Paused)
                    cmdStop();
                mediaPlayer = new MediaPlayer();
                mediaPlayerState = MP_State.Idle;
                mediaPlayer.setOnErrorListener(mediaPlayerOnErrorListener);

                localUrl = Utility.retrieveMediaFileLink(getApplicationContext(), url);
                localFile = true;
                cmdSetDataSource(localUrl);
                save_file.setSelected(true);
//                disableButtonIcon(save_file);
//                save_file.setVisibility(View.GONE);
//                enableButtonIcon(delete_file);
//                delete_file.setVisibility(View.VISIBLE);
//	    		}
            }
        }
    }

    private class PdfExportTask extends AsyncTask<String, Integer, String> {

        public PdfExportTask() {}

        @Override
        protected String doInBackground(String... sUrl) {
            HashMap<String, String> testConv = CambioAccordi.diffSemiToni(primaNota, notaCambio);
            String urlHtml = "";
            if (testConv != null) {
                String nuovoFile = cambiaAccordi(testConv, barreCambio);
                if (nuovoFile != null)
                    urlHtml = nuovoFile;
            }
            else {
                urlHtml = "file:///android_asset/" + pagina + ".htm";
            }
            // step 1
            Float margin = 15f;
            Document document = new Document(PageSize.A4, margin, margin, margin, margin);
            // step 2
            try {
                localPDFPath = "";
                if (Utility.isExternalStorageWritable()) {
                    File[] fileArray = ContextCompat.getExternalFilesDirs(PaginaRenderActivity.this, null);
                    localPDFPath = fileArray[0].getAbsolutePath();
                }
                else {
                    Toast.makeText(PaginaRenderActivity.this
                            , getString(R.string.no_memory_writable), Toast.LENGTH_SHORT).show();
                    this.cancel(true);
                }
                localPDFPath += "/output.pdf";
//				Log.i("localPath", localPDFPath);
                PdfWriter.getInstance(document, new FileOutputStream(localPDFPath));
                // step 3
                document.open();
                Font myFonColor = FontFactory.getFont(FontFactory.COURIER, 14, BaseColor.BLACK);
                // step 4
                try {
                    String line;
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(urlHtml), "UTF-8"));

                    line = br.readLine();
                    while (line != null) {
                        if ((line.contains("000000")
                                || line.contains("A13F3C"))
                                && !line.contains("BGCOLOR")) {
                            if (line.contains("000000")) {
                                myFonColor = FontFactory.getFont(FontFactory.COURIER, 14, BaseColor.BLACK);
                            }

                            if (line.contains("A13F3C")) {
                                myFonColor = FontFactory.getFont(FontFactory.COURIER, 14, BaseColor.RED);
                            }
                            line = line.replaceAll("<H4>", "");
                            line = line.replaceAll("</H4>", "");
                            line = line.replaceAll("<FONT COLOR=\"#000000\">", "");
                            line = line.replaceAll("<FONT COLOR=\"#A13F3C\">", "");
                            line = line.replaceAll("</FONT>", "");
                            line = line.replaceAll("<H5>", "");
                            line = line.replaceAll("<H3>", "");
                            line = line.replaceAll("<H2>", "");
                            line = line.replaceAll("</H5>", "");
                            line = line.replaceAll("</H3>", "");
                            line = line.replaceAll("</H2>", "");
                            line = line.replaceAll("<I>", "");
                            line = line.replaceAll("</I>", "");
                            line = line.replaceAll("<B>", "");
                            line = line.replaceAll("</B>", "");
                            line = line.replaceAll("<br>", "");

//		            		Log.i("LINE", line);
                            Paragraph paragraph = new Paragraph(line, myFonColor);
                            document.add(paragraph);
                        }
                        else {
                            if (line.equals("")) {
                                document.add(Chunk.NEWLINE);
                            }
                        }

                        line = br.readLine();
                    }
                    br.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                //step 5
                document.close();

//		        Log.i("DONE", "PDF Created!");
            }
            catch (FileNotFoundException | DocumentException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            prevOrientation = getRequestedOrientation();
            Utility.blockOrientation(PaginaRenderActivity.this);
            exportDialog.show();
        }

        @Override
        protected void onPostExecute(String result) {
            if (exportDialog.isShowing())
                exportDialog.dismiss();
            File file = new File(localPDFPath);
            Intent target = new Intent(Intent.ACTION_VIEW);
            target.setDataAndType(Uri.fromFile(file),"application/pdf");
            target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            Intent intent = Intent.createChooser(target, getString(R.string.open_pdf));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(PaginaRenderActivity.this
                        , getString(R.string.no_pdf_reader), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableButtonIcon(ImageButton bIcon) {
        bIcon.setEnabled(true);
        bIcon.setColorFilter(getResources().getColor(android.R.color.black));
//        bIcon.getDrawable().setColorFilter(getResources().getColor(android.R.color.black), PorterDuff.Mode.SRC_ATOP);
    }

    private void disableButtonIcon(ImageButton bIcon) {
        bIcon.setEnabled(false);
        bIcon.setColorFilter(getResources().getColor(R.color.item_disabled));
//        bIcon.getDrawable().setColorFilter(getResources().getColor(R.color.item_disabled), PorterDuff.Mode.SRC_ATOP);
    }

    private void initializeLoadingDialogs() {
        mp3Dialog = new ProgressDialogPro(PaginaRenderActivity.this);
        mp3Dialog.setMessage(getResources().getString(R.string.wait));
        mp3Dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                setRequestedOrientation(prevOrientation);
            }
        });

        exportDialog = new ProgressDialogPro(PaginaRenderActivity.this);
        exportDialog.setMessage(getResources().getString(R.string.export_running));
        exportDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                setRequestedOrientation(prevOrientation);
            }
        });

    }

}