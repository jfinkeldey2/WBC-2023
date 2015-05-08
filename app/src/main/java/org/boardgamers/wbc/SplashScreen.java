package org.boardgamers.wbc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SplashScreen extends AppCompatActivity {
  private ProgressBar progressBar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    WBCDataDbHelper dbHelper=new WBCDataDbHelper(this);
    //dbHelper.onUpgrade(dbHelper.getWritableDatabase(), 0, 0);
    dbHelper.getReadableDatabase();
    int totalEvents=dbHelper.getNumEvents();
    dbHelper.close();

    if (totalEvents>0) {
      checkForChanges();
    } else {
      setContentView(R.layout.splash);

      Toolbar toolbar=(Toolbar) findViewById(R.id.toolbar);
      setSupportActionBar(toolbar);
      setTitle(getResources().getString(R.string.activity_splash));

      progressBar=(ProgressBar) findViewById(R.id.splash_progress);
      progressBar.setMax(780);

      new LoadEventsTask(this).execute(0, 0, 0);
    }
  }

  @Override
  public void onBackPressed() {
    showToast("Please wait until all events have loaded");
  }

  public void checkForChanges() {
    String changes="";

    // TODO add changes to database and string here

    if (changes.equalsIgnoreCase("")) {
      startMainActivity();
    } else {
      AlertDialog.Builder changesBuilder=new AlertDialog.Builder(this);
      changesBuilder.setTitle(R.string.changes_dialog_title).setMessage(changes)
          .setNeutralButton(R.string.close, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.dismiss();
              startMainActivity();
            }
          }).setCancelable(false);
      changesBuilder.create().show();
    }
  }

  public void startMainActivity() {
    startActivity(new Intent(this, MainActivity.class));
    finish();
  }

  public void showToast(String string) {
    Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
  }

  /**
   * Load Events Task to load schedule
   */
  public class LoadEventsTask extends AsyncTask<Integer, Integer, Integer> {
    private final static String TAG="Load Events Task";

    private Context context;

    public LoadEventsTask(Context c) {
      context=c;
    }

    @Override
    protected void onPostExecute(Integer result) {
      Intent intent=new Intent(context, UpdateService.class);
      startService(intent);

      if (result>0) {
        startMainActivity();
      } else if (result==-1) {
        showToast("ERROR: Could not parse schedule file, contact dev@boardgamers.org for help.");
      } else if (result==-2) {
        showToast("ERROR: Could not find schedule file, contact dev@boardgamers.org for help.");
      } else if (result==-3) {
        showToast("ERROR: Could not open schedule file, contact dev@boardgamers.org for help.");
      }

      super.onPostExecute(result);
    }

    @Override
    protected Integer doInBackground(Integer... params) {
      // find schedule file
      InputStream is;
      try {
        is=getAssets().open("schedule2014.txt");
      } catch (IOException e2) {
        e2.printStackTrace();
        return -2;
      }
      // read schedule file
      InputStreamReader isr;
      try {
        isr=new InputStreamReader(is);
      } catch (IllegalStateException e1) {
        e1.printStackTrace();
        return -3;
      }

      WBCDataDbHelper dbHelper=new WBCDataDbHelper(context);
      dbHelper.getWritableDatabase();
      // parse schedule file
      BufferedReader reader=new BufferedReader(isr);
      try {
        final String preExtraStrings[]=
            {" AFC", " NFC", " FF", " PC", " Circus", " After Action", " Aftermath"};
        final String postExtraStrings[]={" Demo"};

        final String daysForParsing[]=getResources().getStringArray(R.array.daysForParsing);

        String line;
        String identifier, eventTitle, eClass, format, gm, tempString, location;
        String[] rowData;
        int eventId=1, index, day, hour, prize;
        double duration, totalDuration;
        boolean continuous, qualify, isTournamentEvent;

        long tournamentId;
        String tournamentTitle, tournamentLabel, shortEventTitle="";
        List<String> tournamentTitles=new ArrayList<>();
        while ((line=reader.readLine())!=null) {
          rowData=line.split("~");

          // currentDay
          tempString=rowData[0];
          for (index=0; index<daysForParsing.length; index++) {
            if (daysForParsing[index].equalsIgnoreCase(tempString)) {
              break;
            }
          }
          if (index==-1) {
            Log.d(TAG, "Unknown date: "+rowData[2]+" in "+line);
            index=0;
          }
          day=index;

          // title
          eventTitle=rowData[2];

          // time
          boolean halfPast=false;
          tempString=rowData[1];
          if (tempString.length()>2) {
            Log.d(TAG, rowData[2]+" starts at "+tempString);
            tempString=tempString.substring(0, tempString.indexOf(":"));
            halfPast=true;
          }

          hour=Integer.valueOf(tempString);

          if (rowData.length<8) {
            prize=0;
            eClass="";
            format="";
            duration=0;
            continuous=false;
            gm="";
            location="";
          } else {
            // prize
            tempString=rowData[3];
            if (tempString.equalsIgnoreCase("") || tempString.equalsIgnoreCase("-")) {
              tempString="0";
            }
            prize=Integer.valueOf(tempString);

            // class
            eClass=rowData[4];

            // format
            format=rowData[5];

            // duration
            if (rowData[6].equalsIgnoreCase("") || rowData[6].equalsIgnoreCase("-")) {
              duration=0;
            } else {
              duration=Double.valueOf(rowData[6]);
            }

            if (duration>.33 && duration<.34) {
              duration=.33;
            }

            // continuous
            continuous=rowData[7].equalsIgnoreCase("Y");

            // gm
            gm=rowData[8];

            // location
            location=rowData[9];

          }

          // get tournament title and label and help short name
          tempString=eventTitle;

          // search through extra strings
          for (String eventExtraString : preExtraStrings) {
            index=tempString.indexOf(eventExtraString);
            if (index>-1) {
              tempString=tempString.substring(0, index)+
                  tempString.substring(index+eventExtraString.length());
            }
          }

          isTournamentEvent=eClass.length()>0;
          if (isTournamentEvent || format.equalsIgnoreCase("Preview")) {
            index=tempString.lastIndexOf(" ");
            shortEventTitle=tempString.substring(index+1);
            tempString=tempString.substring(0, index);
          }

          if (eventTitle.contains("Junior") || eventTitle.indexOf("COIN series")==0 ||
              format.equalsIgnoreCase("SOG") || format.equalsIgnoreCase("Preview")) {
            tournamentTitle=tempString;
            tournamentLabel="";

          } else if (isTournamentEvent) {
            for (String eventExtraString : postExtraStrings) {
              index=tempString.indexOf(eventExtraString);
              if (index>-1) {
                tempString=tempString.substring(0, index);
              }
            }

            tournamentLabel=rowData[10];
            tournamentTitle=tempString;
          } else {
            tournamentTitle=tempString;
            tournamentLabel="";

            if (eventTitle.indexOf("Auction")==0) {
              tournamentTitle="AuctionStore";
            }

            // search for non tournament main titles
            String[] nonTournamentStrings=
                {"Open Gaming", "Registration", "Vendors Area", "World at War", "Wits & Wagers",
                    "Texas Roadhouse BPA Fundraiser", "Memoir: D-Day"};
            for (String nonTournamentString : nonTournamentStrings) {
              if (tempString.indexOf(nonTournamentString)==0) {
                tournamentTitle=nonTournamentString;
                break;
              }
            }
          }

          for (index=tournamentTitles.size()-1; index>-1; index--) {
            if (tournamentTitles.get(index).equalsIgnoreCase(tournamentTitle)) {
              break;
            }
          }

          tournamentId=index+1;
          if (tournamentId==0) {
            tournamentId=dbHelper
                .insertTournament(tournamentTitle, tournamentLabel, isTournamentEvent, prize, gm,
                    eventId);
            tournamentTitles.add(tournamentTitle);
          }

          // Log.d(TAG, String.valueOf(tournamentId)+": "+tournamentTitle
          // +";;;E: "+eventTitle);

          totalDuration=duration;
          qualify=false;

          if (isTournamentEvent || format.equalsIgnoreCase("Junior")) {
            if (shortEventTitle.equals("SF")) {
              qualify=true;
            } else if (shortEventTitle.equals("QF")) {
              qualify=true;
            } else if (shortEventTitle.equals("F")) {
              qualify=true;
            } else if (shortEventTitle.equals("QF/SF/F")) {
              qualify=true;
              totalDuration*=3;
            } else if (shortEventTitle.equals("SF/F")) {
              qualify=true;
              totalDuration*=2;
            } else if (continuous && shortEventTitle.indexOf("R")==0 &&
                shortEventTitle.contains("/")) {
              int dividerIndex=shortEventTitle.indexOf("/");
              int startRound=Integer.valueOf(shortEventTitle.substring(1, dividerIndex));
              int endRound=Integer.valueOf(shortEventTitle.substring(dividerIndex+1));

              int currentTime=hour;
              for (int round=0; round<endRound-startRound; round++) {
                // if time passes midnight, next round starts at 9 the next currentDay
                if (currentTime>24) {
                  if (currentTime>=24+9) {
                    Log.d(TAG, "Event "+eventTitle+" goes past 9");
                  }
                  totalDuration+=9-(currentTime-24);
                  currentTime=9;
                }
                totalDuration+=duration;
                currentTime+=duration;
              }
            } else if (continuous) {
              Log.d(TAG, "Unknown continuous event: "+eventTitle);
            }
          }

          if (halfPast) {
            eventTitle=eventTitle+" ("+rowData[1]+")";
          }

          identifier=String.valueOf(day*24+hour)+eventTitle;

          dbHelper
              .insertEvent(identifier, tournamentId, day, hour, eventTitle, eClass, format, qualify,
                  duration, continuous, totalDuration, location, false);

          progressBar.setProgress(eventId);
          eventId++;
        }

        // close streams and number of events
        isr.close();
        is.close();
        reader.close();
        dbHelper.close();

        // log statistics
        Log.d(TAG,
            "Finished load, "+String.valueOf(tournamentTitles.size())+" total tournaments and "+
                String.valueOf(eventId)+" total events");
        return eventId;
      } catch (IOException e) {
        e.printStackTrace();
        return -1;
      }
    }
  }
}
