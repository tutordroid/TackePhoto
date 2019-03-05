package com.example.tackephoto;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.tackephoto.parser.JSONParser;
import com.example.tackephoto.permission.PermissionsActivity;
import com.example.tackephoto.permission.PermissionsChecker;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String[] PERMISSIONS_READ_STORAGE = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

    Context mContext;
    String imagePath;
    PermissionsChecker checker;
    File destination;

    ImageView img1;

    Config cg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cg = new Config();
        mContext = getApplicationContext();
        checker = new PermissionsChecker(MainActivity.this);

        img1 = (ImageView) findViewById(R.id.img1);

        Button btnTake = (Button) findViewById(R.id.btnTake);
        btnTake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTakeType();
            }
        });
    }

    private void selectTakeType() {

        if (checker.lacksPermissions(PERMISSIONS_READ_STORAGE)) {
            startPermissionsActivity(PERMISSIONS_READ_STORAGE);
        } else {
            showImagePopup();
        }
    }


    public void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        switch (requestCode) {
            case 0:
                if (resultCode == RESULT_OK) {
                    FileInputStream in = null;
                    try {
                        in = new FileInputStream(destination);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 10;
                    imagePath = destination.getAbsolutePath();

                    Bitmap bmp = BitmapFactory.decodeStream(in, null, options);
                    //imageview.setImageBitmap(bmp);

                    Log.d("imagePath", imagePath);

                    String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());

                    ///// Resize
                    Log.d("compressBitmap", "fileNameTake: " + imagePath + currentDateTimeString);
                    // resizeAndCompressImageBeforeSend(getContext(), imagePath, currentDateTimeString);
                    resizePhoto(getApplicationContext(), imagePath, destination.getName());
                    ///// end Resize

                }

                break;
            case 1010:
                if (imageReturnedIntent == null) {
                    Toast.makeText(MainActivity.this, "" + getResources().getString(R.string.string_unable_to_pick_image), Toast.LENGTH_LONG).show();
                    return;
                }
                Uri selectedImageUri = imageReturnedIntent.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImageUri, filePathColumn, null, null, null);

                if (cursor != null) {
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);


                    imagePath = cursor.getString(columnIndex);
                    String fNameSelect = "";
                    int cut = imagePath.lastIndexOf('/');
                    if (cut != -1) {
                        fNameSelect = imagePath.substring(cut + 1);
                    }

                    Log.d("imagePath", imagePath+ "fNameSelect=" +fNameSelect);

                    //Picasso.with(mContext).load(new File(imagePath)).into(imageview);
                    cursor.close();
                    String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
                    //savePhoto(currentDateTimeString);
                    resizePhoto(getApplicationContext(), imagePath, fNameSelect);

                } else {
                    Toast.makeText(MainActivity.this, "" + getResources().getString(R.string.string_unable_to_load_image), Toast.LENGTH_LONG).show();
                }

                //imageview.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void resizePhoto(final Context context, final String fPath, final String fileName) {
        if (!TextUtils.isEmpty(fPath)) {


            new AsyncTask<Void, Integer, String>() {

                ProgressDialog progressDialog;

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage("Uploading...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }

                @Override
                protected String doInBackground(Void... params) {
                    String p = ":";
                    final int MAX_IMAGE_SIZE = 800 * 600; // max final file size in kilobytes

                    // First decode with inJustDecodeBounds=true to check dimensions of image
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(fPath, options);

                    // Calculate inSampleSize(First we are going to resize the image to 800x800 image, in order to not have a big but very low quality image.
                    //resizing the image will already reduce the file size, but after resizing we will check the file size and start to compress image
                    options.inSampleSize = calculateInSampleSize(options, 800, 600);

                    // Decode bitmap with inSampleSize set
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    Bitmap bmpPic = BitmapFactory.decodeFile(fPath, options);

                    int compressQuality = 100; // quality decreasing by 5 every loop.
                    int streamLength;
                    do {
                        ByteArrayOutputStream bmpStream = new ByteArrayOutputStream();
                        Log.d("compressBitmap", "Quality: " + compressQuality);
                        bmpPic.compress(Bitmap.CompressFormat.JPEG, compressQuality, bmpStream);
                        byte[] bmpPicByteArray = bmpStream.toByteArray();
                        streamLength = bmpPicByteArray.length;
                        compressQuality -= 5;
                        Log.d("compressBitmap", "Size: " + streamLength / 1024 + " kb");
                    } while (streamLength >= MAX_IMAGE_SIZE);

                    try {
                        //save the resized and compressed file to disk cache
                        Log.d("compressBitmap", "cacheDir: " + context.getCacheDir());
                        FileOutputStream bmpFile = new FileOutputStream(context.getCacheDir() + fileName);
                        bmpPic.compress(Bitmap.CompressFormat.JPEG, compressQuality, bmpFile);
                        bmpFile.flush();
                        bmpFile.close();
                    } catch (Exception e) {
                        Log.e("compressBitmap", "Error on saving file");
                    }
                    imagePath = context.getCacheDir() + fileName;
                    return context.getCacheDir() + fileName;

                }

                @Override
                protected void onPostExecute(String aBoolean) {
                    super.onPostExecute(aBoolean);
                    if (progressDialog != null)
                        progressDialog.dismiss();

                    savePhoto(aBoolean);
                    Log.e("compressBitmap", "aBoolean " + aBoolean);
                }
            }.execute();

        } else {
            Toast.makeText(MainActivity.this, R.string.string_message_to_attach_file, Toast.LENGTH_LONG).show();
        }
    }

    private void savePhoto(final String datenowpost) {
        if (!TextUtils.isEmpty(imagePath)) {


            new AsyncTask<Void, Integer, Boolean>() {

                ProgressDialog progressDialog;

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage("Upload..");
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }

                @Override
                protected Boolean doInBackground(Void... params) {

                    try {

                        JSONObject jsonObject = JSONParser.uploadPraImage(imagePath);
                        if (jsonObject != null)
                            return jsonObject.getString("result").equals("success");

                    } catch (JSONException e) {
                        Log.i("TAG", "Error : " + e.getLocalizedMessage());
                    }
                    return false;
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    super.onPostExecute(aBoolean);
                    if (progressDialog != null)
                        progressDialog.dismiss();

                    if (aBoolean) {
                        Toast.makeText(MainActivity.this, "อัพโหลดสำเร็จ", Toast.LENGTH_LONG).show();

                        //Picasso.get().load(cg.getUrlUpload()+"http://i.imgur.com/DvpvklR.png").into(img1);


                    } else {
                        Toast.makeText(MainActivity.this, "อัพโหลดผิดพลาด", Toast.LENGTH_LONG).show();
                        //imagePath = "";
                    }
                }
            }.execute();

        } else {
            Toast.makeText(MainActivity.this, R.string.string_message_to_attach_file, Toast.LENGTH_LONG).show();

        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        String debugTag = "MemoryInformation";
        // Image nin islenmeden onceki genislik ve yuksekligi
        final int height = options.outHeight;
        final int width = options.outWidth;
        Log.d(debugTag, "image height: " + height + "---image width: " + width);
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        Log.d(debugTag, "inSampleSize: " + inSampleSize);
        return inSampleSize;
    }

    private void showImagePopup() {
        // File System.
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_PICK);

        // Chooser of file system options.
        final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.string_choose_image));
        startActivityForResult(chooserIntent, 1010);
    }


    private void startPermissionsActivity(String[] permission) {
        PermissionsActivity.startActivityForResult(MainActivity.this, 0, permission);
    }

    public String dateToString(Date date, String format) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        return df.format(date);
    }
}
