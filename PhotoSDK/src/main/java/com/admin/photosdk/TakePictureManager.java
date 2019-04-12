package com.admin.photosdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.view.Gravity;
import android.view.View;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * 作者：create by comersss on 2018/12/14 10:55
 * 邮箱：904359289@qq.com
 * 拍照或者从相册获取照片、裁剪等操作工具类
 */

public class TakePictureManager {
    private Fragment mFragment;
    private boolean isActicity;
    //默认不开启裁剪
    private boolean isTailor = false;
    //裁剪宽高的比例,默认是是 1 ：1
    private int aspectX = 1;
    private int aspectY = 1;
    //裁剪图片的宽高,默认是是 1 ：1
    private int outputX = 450;
    private int outputY = 450;
    //拿到未裁剪相片的回调码（拍照后）
    private static final int CODE_ORIGINAL_PHOTO_CAMERA = 101;
    //拿到未裁剪相片的回调码（选择本地图库后）
    private static final int CODE_ORIGINAL_PHOTO_ALBUM = 102;
    //拿到已裁剪相片的回调码
    private static final int CODE_TAILOR_PHOTO = 103;
    //上下文
    private Context mContext;
    private Activity mActivity;
    //FileProvider的主机名：一般是包名+".fileprovider"
    private String FILE_PROVIDER_AUTHORITY;
    //临时存储相片地址
    private String imgPath;
    //图片回调接口
    private takePictureCallBackListener takeCallBacklistener;
    private Bitmap outBitmap;
    private Uri outputUri;
    private String outImageUrl;
    private String imageType = "6";

    //得到图片回调接口
    public interface takePictureCallBackListener {
        void successful(Bitmap bitmap);
    }

    private PhotoPopupWindow mPhotoPopupWindow;

    //图片类型 1.身份证正面  2.身份证反面  3.营业执照  4.开户许可证  5.门头照  6.门店icon 7.银行卡正面照 8.手持银行卡照 9.收银员头像 21.问题反馈图片 99.特殊照片
    public TakePictureManager(Activity activity, String type, takePictureCallBackListener listener) {
        this.mActivity = activity;
        this.imageType = type;
        mContext = activity;
        isActicity = true;
        this.takeCallBacklistener = listener;
        FILE_PROVIDER_AUTHORITY = mActivity.getPackageName() + ".fileprovider";
    }

    //图片类型 1.身份证正面  2.身份证反面  3.营业执照  4.开户许可证  5.门头照  6.门店icon 7.银行卡正面照 8.手持银行卡照 9.收银员头像 21.问题反馈图片 99.特殊照片
    public TakePictureManager(Fragment mFragment, takePictureCallBackListener listener) {
        this.mActivity = mFragment.getActivity();
        this.mFragment = mFragment;
        isActicity = false;
        mContext = mFragment.getActivity();
        this.takeCallBacklistener = listener;
        FILE_PROVIDER_AUTHORITY = mActivity.getPackageName() + ".fileprovider";
    }

    /**
     * 拍照或调取相册
     */
    public void takePic() {
        PermissionUtils.permission(PermissionConstants.STORAGE, PermissionConstants.CAMERA).callback(new PermissionUtils.SimpleCallback() {
            @Override
            public void onGranted() {
                mPhotoPopupWindow = new PhotoPopupWindow(mActivity, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPhotoPopupWindow.dismiss();
                        //从相册选取
                        imgPath = generateImgePath(mContext);
                        Intent intent = new Intent(Intent.ACTION_PICK, null);
                        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "image/*");
                        if (isActicity) {
                            mActivity.startActivityForResult(intent, CODE_ORIGINAL_PHOTO_ALBUM);
                        } else {
                            mFragment.startActivityForResult(intent, CODE_ORIGINAL_PHOTO_ALBUM);
                        }
                    }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPhotoPopupWindow.dismiss();
                        startOpencamera();

                    }
                });
                mPhotoPopupWindow.showAtLocation(mActivity.getWindow().getDecorView(),
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            }

            @Override
            public void onDenied() {
                ToastUtils.showShort("请授权");
            }

        }).request();
    }

    /**
     * 对外接口，是否裁剪
     *
     * @param aspectX 要裁剪的宽比例
     * @param aspectY 要裁剪的高比例
     * @param outputX 要裁剪图片的宽
     * @param outputY 要裁剪图片的高
     */
    public void setTailor(int aspectX, int aspectY, int outputX, int outputY) {
        isTailor = true;
        this.aspectX = aspectX;
        this.aspectY = aspectY;
        this.outputX = outputX;
        this.outputY = outputY;
    }

    /**
     * 裁剪方法
     */
    private void statZoom(File srcFile, File output) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.setDataAndType(getImageContentUri(mContext, srcFile), "image/*");
        // crop为true是设置在开启的intent中设置显示的view可以剪裁
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);// 去黑边
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", aspectX);
        intent.putExtra("aspectY", aspectY);
        // outputX,outputY 是剪裁图片的宽高
        intent.putExtra("outputX", outputX);
        intent.putExtra("outputY", outputY);
        intent.putExtra("return-data", false);// true:不返回uri，false：返回uri
        intent.putExtra("scaleUpIfNeeded", true);//黑边
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        mActivity.startActivityForResult(intent, CODE_TAILOR_PHOTO);
    }

    /**
     * 获取到的相片回调方法
     */
    public void attachToActivityForResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != -1) {
            return;
        }
        File temFile;
        File srcFile;
        File outPutFile;
        switch (requestCode) {
            //拍照后得到的图片
            case CODE_ORIGINAL_PHOTO_CAMERA:
                srcFile = new File(imgPath);
                outPutFile = new File(generateImgePath(mContext));
                outputUri = Uri.fromFile(outPutFile);
                if (isTailor) {
                    statZoom(srcFile, outPutFile);
                } else {
                    Uri imageContentUri = getImageContentUri(mContext, srcFile);
                    try {
                        outBitmap = getBitmapFormUri(mActivity, imageContentUri);
                        outBitmap = rotaingImageView(outBitmap, getPathByUri(imageContentUri));
                        takeCallBacklistener.successful(outBitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            //选择相册后得到的图片
            case CODE_ORIGINAL_PHOTO_ALBUM:
                if (data != null) {
                    outPutFile = new File(generateImgePath(mContext));
                    outputUri = Uri.fromFile(outPutFile);
                    if (isTailor) {
                        Uri sourceUri = data.getData();
                        String pickPath = getPath(mContext, sourceUri);
                        srcFile = new File(pickPath);
                        //裁剪之后的文件和ur
                        //发起裁剪请求
                        statZoom(srcFile, outPutFile);
                    } else {
                        try {
                            outputUri = data.getData();
                            outBitmap = getBitmapFormUri(mActivity, outputUri);
                            outBitmap = rotaingImageView(outBitmap, getPathByUri(outputUri));
                            takeCallBacklistener.successful(outBitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            //裁剪后的图片：
            case CODE_TAILOR_PHOTO:
                //拿到图片之后，用户可能会舍弃，所以先判断
                if (data != null) {
                    if (outputUri != null) {
                        //如果是拍照的,删除临时文件
                        temFile = new File(imgPath);
                        if (temFile.exists()) {
                            temFile.delete();
                        }
                        outBitmap = compressImage(decodeUriAsBitmap(outputUri));
                        takeCallBacklistener.successful(outBitmap);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * 打开相机
     */
    private void startOpencamera() {
        imgPath = generateImgePath(mContext);
        File imgFile = new File(imgPath);
        Uri imgUri = null;
        //判断当前手机版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imgUri = FileProvider.getUriForFile(mContext, FILE_PROVIDER_AUTHORITY, imgFile);
        } else {
            imgUri = Uri.fromFile(imgFile);
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);
        mActivity.startActivityForResult(intent, CODE_ORIGINAL_PHOTO_CAMERA);
    }

    private static final String ICON_DIR = "icon";
    private static final String APP_STORAGE_ROOT = "DailyPay";

    /**
     * 判断SD卡是否挂载
     */
    private static boolean isSDCardAvailable() {
        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取app在外置SD卡的路径
     */
    private static String getAppDir(Context context, String name) {
        StringBuilder sb = new StringBuilder();
        if (isSDCardAvailable()) {
            sb.append(getAppExternalStoragePath());
        } else {
            sb.append(getCachePath(context));
        }
        sb.append(name);
        sb.append(File.separator);
        String path = sb.toString();
        if (createDirs(path)) {
            return path;
        } else {
            return null;
        }
    }

    /**
     * 获取SD下当前APP的目录
     */
    private static String getAppExternalStoragePath() {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory().getAbsolutePath());
        sb.append(File.separator);
        sb.append(APP_STORAGE_ROOT);
        sb.append(File.separator);
        return sb.toString();
    }

    /**
     * 获取应用的cache目录
     */
    private static String getCachePath(Context context) {
        File f = context.getCacheDir();
        if (null == f) {
            return null;
        } else {
            return f.getAbsolutePath() + "/";
        }
    }

    /**
     * 创建文件夹
     */
    private static boolean createDirs(String dirPath) {
        File file = new File(dirPath);
        if (!file.exists() || !file.isDirectory()) {
            return file.mkdirs();
        }
        return true;
    }

    /**
     * 产生图片的路径，带文件夹和文件名，文件名为当前毫秒数
     */
    private static String generateImgePath(Context context) {
        return getAppDir(context, ICON_DIR) + String.valueOf(System.currentTimeMillis()) + ".jpg";
    }

    /**
     * 裁剪根据文件路径获取uri
     */
    private static Uri getImageContentUri(Context context, File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor
                    .getColumnIndex(MediaStore.MediaColumns._ID));
            Uri baseUri = Uri.parse("content://media/external/images/media");
            return Uri.withAppendedPath(baseUri, "" + id);
        } else {
            if (imageFile.exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, filePath);
                return context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                return null;
            }
        }
    }

    /**
     * 根据uri返回bitmap
     */
    public Bitmap decodeUriAsBitmap(Uri uri) {
        Bitmap bitmap = null;
        try {
            // 先通过getContentResolver方法获得一个ContentResolver实例，
            // 调用openInputStream(Uri)方法获得uri关联的数据流stream
            // 把上一步获得的数据流解析成为bitmap
            bitmap = BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(uri));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return bitmap;
    }

    /**
     * 返回一张压缩后的图片
     */
    private static Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);//质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
        int baosLength = baos.toByteArray().length;
        while (baosLength / 1024 > 100) {    //循环判断如果压缩后图片是否大于100kb,大于继续压缩
            baos.reset();//重置baos即让下一次的写入覆盖之前的内容
            options = Math.max(0, options - 10);//图片质量每次减少10
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);//将压缩后的图片保存到baos中
            baosLength = baos.toByteArray().length;
            if (options == 0)//如果图片的质量已降到最低则，不再进行压缩
                break;
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());//把压缩后的数据baos存放到ByteArrayInputStream中
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);//把ByteArrayInputStream数据生成图片
        return bitmap;
    }


    /**
     * 读取照片旋转角度
     *
     * @param path 照片路径
     * @return 角度
     */
    public static int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /* 旋转图片
     *
     * @param angle  被旋转角度
     * @param bitmap 图片对象
     * @return 旋转后的图片
     */
    public static Bitmap rotaingImageView(Bitmap bitmap, String path) {
        int angle = readPictureDegree(path);
        Bitmap returnBm = null;
        // 根据旋转角度，生成旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        try {
            // 将原始图片按照旋转矩阵进行旋转，并得到新的图片
            returnBm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
        }
        if (returnBm == null) {
            returnBm = bitmap;
        }
        if (bitmap != returnBm) {
            bitmap.recycle();
        }
        return returnBm;
    }

    //uri转bitmap
    public static Bitmap getBitmapFormUri(Activity ac, Uri uri) throws IOException {
        InputStream input = ac.getContentResolver().openInputStream(uri);
        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither = true;//optional
        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
        input.close();
        int originalWidth = onlyBoundsOptions.outWidth;
        int originalHeight = onlyBoundsOptions.outHeight;
        if ((originalWidth == -1) || (originalHeight == -1))
            return null;
        //图片分辨率以480x800为标准
        float hh = 800f;//这里设置高度为800f
        float ww = 480f;//这里设置宽度为480f
        //缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;//be=1表示不缩放
        if (originalWidth > originalHeight && originalWidth > ww) {//如果宽度大的话根据宽度固定大小缩放
            be = (int) (originalWidth / ww);
        } else if (originalWidth < originalHeight && originalHeight > hh) {//如果高度高的话根据宽度固定大小缩放
            be = (int) (originalHeight / hh);
        }
        if (be <= 0)
            be = 1;
        //比例压缩
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = be;//设置缩放比例
        bitmapOptions.inDither = true;
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        input = ac.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
        input.close();
        return compressImage(bitmap);//再进行质量压缩
    }

    public String getPathByUri(Uri uri) {
        String path;
        if (Build.VERSION.SDK_INT >= 19) {
            //4.4及以上系统使用这个方法处理图片
            path = getPath(mContext, uri);
        } else {
            //4.4以下使用这个方法处理图片
            path = getImagePath(uri, null);
        }
        return path;
    }

    //通过Uri和selection来获取真实的图片路径
    private String getImagePath(Uri uri, String selection) {
        String path = null;
        Cursor cursor = mActivity.getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    /**
     * -适配小米手机
     **/
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection,
                        selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;

    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;

    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

}
