package org.secuso.privacyfriendlywerwolf.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.secuso.privacyfriendlywerwolf.R;
import org.secuso.privacyfriendlywerwolf.context.GameContext;
import org.secuso.privacyfriendlywerwolf.dialog.GameInformationDialog;
import org.secuso.privacyfriendlywerwolf.helpers.PermissionHelper;
import org.secuso.privacyfriendlywerwolf.model.Player;
import org.secuso.privacyfriendlywerwolf.server.ServerGameController;
import org.secuso.privacyfriendlywerwolf.util.Constants;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * StartHostActivity is the default page to start a game host
 * It waits for other clients to connect by creating a new Thread
 *
 * @author Florian Staubach <florian.staubach@stud.tu-darmstadt.de>
 * @author Tobias Kowalski <tobias.kowalski@stud.tu-darmstadt.de>
 */
public class StartHostActivity extends BaseActivity {
    private ArrayList<String> stringPlayers;
    private LinearLayoutCompat list;

    /**
     * statics
     */
    private static final String TAG = "StartHostActivity";
    private static final int MIN_PLAYER_COUNT = 6;

    /**
     * controller
     */
    private ServerGameController serverGameController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serverGameController = serverGameController.getInstance();
        serverGameController.setStartHostActivity(this);
        //reset everything
        serverGameController.destroy();

        setContentView(R.layout.activity_start_host);

        applyConnectionInfo();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(R.string.startgame_subtitle);

        PermissionHelper.showWifiAlert(this);

        // start the server
        serverGameController.startServer();

        Button buttonStart = findViewById(R.id.btn_start);

        // user clicks the button to start the game
        buttonStart.setOnClickListener(new View.OnClickListener() {
            StartHostActivity activity;

            private View.OnClickListener init(StartHostActivity activity) {
                this.activity = activity;
                return this;
            }

            @Override
            public void onClick(View view) {
                int players = serverGameController.getGameContext().getPlayersList().size();
                if (players >= MIN_PLAYER_COUNT) {
                    openGameInformationDialog();
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.startgame_need_players)
                            .setMessage(R.string.startgame_need_players_message)
                            .setPositiveButton(R.string.button_okay, (dialog, which) -> openGameInformationDialog())
                            .setNegativeButton(android.R.string.no, (dialog, which) -> {
                                // just close and wait for more players
                            })
                            .setIcon(R.drawable.ic_face_black_24dp)
                            .setCancelable(false)
                            .show();
                }
            }
        }.init(this));

        list = findViewById(R.id.host_player_list);

        stringPlayers = new ArrayList<>();
        fillStringPlayers();

        Intent intent = getIntent();
        serverGameController.prepareServerPlayer(intent.getStringExtra(Constants.PLAYERNAME_PUTEXTRA));
    }

    private void openGameInformationDialog() {
        GameInformationDialog dialog = new GameInformationDialog();
        dialog.setAmountOfPlayers(stringPlayers.size());
        dialog.setStartHostActivity(this);
        dialog.setCancelable(false);
        dialog.show(getSupportFragmentManager(), "gameInformationDialog");
    }

    /**
     * fill the lobby list
     */
    private void fillStringPlayers() {
        stringPlayers.clear();
        List<Player> players = GameContext.getInstance().getPlayersList();
        for (Player player : players) {
            stringPlayers.add(player.getPlayerName());
        }
    }

    /**
     * update the ui
     */
    public void renderUI() {
        fillStringPlayers();
        runOnUiThread(() -> {
            list.removeAllViews();
            for (String stringPlayer : stringPlayers) {
                TextView view = (TextView) getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                view.setText(stringPlayer);
                list.addView(view);
            }
        });
    }

    /**
     * Get the info message containing details for clients to connect to this host
     */
    private void applyConnectionInfo() {
        TextView connection_info = findViewById(R.id.connection_info);
        ImageView qr_view = findViewById(R.id.qr_view);

        String result = "";
        if (PermissionHelper.getHotspotSSID() != null) {
            String ssid = PermissionHelper.getHotspotSSID();
            String passphrase = PermissionHelper.getHotspotPassphrase();
            result += getResources().getString(R.string.startgame_hotspot_details, ssid, passphrase);

            qr_view.setVisibility(View.VISIBLE);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            try {
                BitMatrix bitMatrix = qrCodeWriter.encode(PermissionHelper.getHotspotQR(), BarcodeFormat.QR_CODE, 200, 200);
                Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565);
                for (int x = 0; x < 200; x++) {
                    for (int y = 0; y < 200; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                qr_view.setImageBitmap(bitmap);
            } catch (WriterException e) {
                Log.e(TAG, "Could not create QR-Code bitmap", e);
                qr_view.setVisibility(View.GONE);
            }
        } else {
            qr_view.setVisibility(View.GONE);
        }
        if (PermissionHelper.getHotspotSSID() == null && !PermissionHelper.isWifiEnabled(this)) {
            connection_info.setText(getResources().getString(R.string.text_view_enable_wifi));
        } else {
            try {
                Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (enumNetworkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                    Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                    while (enumInetAddress.hasMoreElements()) {
                        InetAddress inetAddress = enumInetAddress.nextElement();

                        if (inetAddress.isSiteLocalAddress()) {
                            if (!result.isEmpty()) result += "\n";
                            result += getResources().getString(R.string.startgame_use_this_ip, inetAddress.getHostAddress(), networkInterface.getDisplayName());
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
                result += "Something Wrong! " + e + "\n";
            }
            connection_info.setText(result);
        }
    }

    /**
     * start the game
     */
    public void startGame() {
        serverGameController.initiateGame();
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("Host", true);
        startActivity(intent);

    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        // erase backstack (pressing back-button now leads to home screen)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.handlePermissionRequestResult(this, requestCode, permissions, grantResults);
    }
}