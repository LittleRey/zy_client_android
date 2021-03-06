/**
 * Copyright 2020 javakam
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zy.client.utils.ext

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.zy.client.App
import com.zy.client.R
import com.zy.client.utils.NoShakeClickListener
import java.io.OutputStream
import java.util.*

/**
 * Title: 扩展函数
 */

fun View?.visibleOrGone(visible: Boolean) {
    this?.run {
        if (visible) {
            if (!isVisible) visibility = View.VISIBLE
        } else {
            if (isVisible) visibility = View.GONE
        }
    }
}

fun View?.visibleOrInvisible(visible: Boolean) {
    this?.run {
        if (visible) {
            if (!isVisible) visibility = View.VISIBLE
        } else {
            if (isVisible) visibility = View.INVISIBLE
        }
    }
}

fun View?.visible() {
    this?.run {
        if (!isVisible) visibility = View.VISIBLE
    }
}

fun View?.invisible() {
    this?.run {
        if (isVisible) visibility = View.INVISIBLE
    }
}

fun View?.gone() {
    this?.run {
        if (isVisible) visibility = View.GONE
    }
}

fun View?.noShake(block: (v: View?) -> Unit) {
    this?.apply {
        setOnClickListener(object : NoShakeClickListener() {
            override fun onSingleClick(v: View?) {
                block.invoke(v)
            }
        })
    }
}

fun String?.noNull(default: String? = ""): String {
    return if (isNullOrBlank()) default ?: "" else this
}

/**
 * 可以在应用内播放的地址
 */
fun String?.isVideoUrl(): Boolean {
    return (this.noNull()).toLowerCase(Locale.getDefault()).run {
        endsWith(".m3u8")
                || endsWith(".mp4")
                || endsWith(".mkv")
                || endsWith(".flv")
                || endsWith(".avi")
                || endsWith(".rm")
                || endsWith(".rmvb")
                || endsWith(".wmv")
    }
}

fun String.copyToClipBoard() {
    val cm: ClipboardManager? =
        App.instance.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?
    if (cm != null) {
        cm.setPrimaryClip(ClipData.newPlainText(null, this))//参数一：标签，可为空，参数二：要复制到剪贴板的文本
        if (cm.hasPrimaryClip()) {
            cm.primaryClip?.getItemAt(0)?.text
        }
    }
}

fun Context.hideSoftInput(activity: Activity) {
    val view: View = activity.currentFocus ?: activity.window.decorView
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)
        ?.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Context.browser(url: String, newTask: Boolean = false): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        //startActivity(intent)
        //https://developer.android.com/about/versions/11/privacy/package-visibility
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(intent, "请选择浏览器"))
        } else {
            toastShort("没有可用浏览器")
        }
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }
}

/**
 * 屏幕的宽度 screen width in pixels
 */
val Context.screenWidth: Int
    get() = resources.displayMetrics.widthPixels

/**
 * 屏幕的高度 screen height in pixels
 */
val Context.screenHeight: Int
    get() = resources.displayMetrics.heightPixels

//MediaStore
//------------------------------------------------------------------------------------------------

/**
 * ContentValues
 * <pre>
 * values.put(MediaStore.Images.Media.IS_PENDING, isPending)
 * Android Q , MediaStore中添加 MediaStore.Images.Media.IS_PENDING flag，用来表示文件的 isPending 状态，0是可见，其他不可见
 * </pre>
 * @param displayName 文件名
 * @param description 描述
 * @param mimeType 媒体类型
 * @param title 标题
 * @param relativePath 相对路径 eg: ${Environment.DIRECTORY_PICTURES}/app_name , 默认为文件管理中的 Picture 目录(Environment.DIRECTORY_PICTURES)
 * @param isPending 默认0 , 0是可见，其他不可见
 */
fun createContentValues(
    displayName: String? = null,
    description: String? = null,
    mimeType: String? = null,
    title: String? = null,
    relativePath: String? = null,
    isPending: Int? = 1,
): ContentValues {
    return ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.DESCRIPTION, description)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.TITLE, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, isPending)
        }
    }
}

/**
 * android.permission.WRITE_EXTERNAL_STORAGE
 */
fun insertBitmap(
    context: Context,
    bitmap: Bitmap?,
    values: ContentValues,
    block: (v: Uri?) -> Unit = {}
): Uri? {
    val externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val resolver = context.contentResolver
    val insertUri = resolver.insert(externalUri, values)
    //标记当前文件是 Pending 状态
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        //MediaStore.setIncludePending(insertUri)
    }
    var os: OutputStream? = null
    try {
        if (insertUri != null && bitmap != null) {
            os = resolver.openOutputStream(insertUri)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
            os?.flush()

            Log.i("123", "创建Bitmap成功 insertBitmap $insertUri")

            //https://developer.android.google.cn/training/data-storage/files/media#native-code
            // Now that we're finished, release the "pending" status, and allow other apps
            // to view the image.
            values.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(insertUri, values, null, null)
            }
        }
        block.invoke(insertUri)
    } catch (e: Exception) {
        Log.e("123", "创建失败：${e.message}")
    } finally {
        if (bitmap?.isRecycled == false) bitmap.recycle()
        os?.close()
        return insertUri
    }
}

///////////////////////////////////////////Glide
/**
 * Glide+RecyclerView卡在placeHolder视图 , 不显示加载成功图片的问题
 * <pre>
 * https://www.cnblogs.com/jooy/p/12186977.html
</pre> *
 */
fun noAnimate(placeholder: Int = -1): RequestOptions {
    var options = RequestOptions()
        .centerCrop()
        .dontAnimate()
    if (placeholder > 0) {
        options = options.placeholder(placeholder)
    }
    return options
}

fun noAnimate(placeholder: Int = -1, error: Int = -1): RequestOptions {
    var options = RequestOptions()
        .centerCrop()
        .dontAnimate()
    if (placeholder > 0) {
        options = options.placeholder(placeholder)
    }
    if (error > 0) {
        options = options.error(error)
    }
    return options
}

fun loadImage(imageView: ImageView, url: String?, placeholder: Int = R.drawable.ic_placeholder) {
    if (url != null && url.startsWith("http")) {
        Glide.with(imageView.context)
            .asBitmap()
            .centerCrop()
            .load(url)
            .skipMemoryCache(false)
            .apply(noAnimate(placeholder))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    } else {
        Glide.with(imageView.context).asBitmap().load(placeholder).centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    }.into(imageView)
}

fun loadImage(imageView: ImageView, path: Any?, placeholder: Drawable?) {
    val options = RequestOptions()
        .centerCrop()
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .placeholder(placeholder)

    Glide.with(imageView.context)
        .load(path)
        .apply(options)
        .transition(DrawableTransitionOptions.withCrossFade())
        .dontAnimate()
        .into(imageView)
}