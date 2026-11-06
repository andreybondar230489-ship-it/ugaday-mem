package com.example.ugadaymem

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlin.random.Random

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var folder: Uri? = null
    private var memes: List<Uri> = emptyList()
    private var selected: Set<Uri> = emptySet()
    private var order: List<Uri> = emptyList()
    private var index = 0
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var rounds: EditText
    private lateinit var perRound: EditText
    private lateinit var mode: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folder = prefs.getString("folder", null)?.let(Uri::parse)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xff111827.toInt())
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val display = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun label(s: String): TextView = TextView(this).apply {
            text=s; textSize=17f; setTextColor(-1); setPadding(4,8,4,8)
        }
        fun button(s:String, action:()->Unit): Button = Button(this).apply { text=s; setOnClickListener { action() } }
        controls.addView(label("УГАДАЙ МЕМ · ведущий"))
        controls.addView(button("Выбрать папку один раз") { chooseFolder() })
        controls.addView(button("Обновить мемы") { scan() })
        rounds = EditText(this).apply { setText("3"); inputType=2; hint="Раунды"; setTextColor(-1) }
        perRound = EditText(this).apply { setText("5"); inputType=2; hint="Мемов в раунде"; setTextColor(-1) }
        controls.addView(label("Количество раундов")); controls.addView(rounds)
        controls.addView(label("Мемов в раунде")); controls.addView(perRound)
        mode = Switch(this).apply { text="Только отмеченные"; setTextColor(-1) }
        controls.addView(mode)
        controls.addView(button("Отметить мемы") { chooseMemes() })
        controls.addView(button("Случайно распределить и начать") { startGame() })
        controls.addView(button("Следующий мем") { nextMeme() })
        status=label("Папка не выбрана")
        controls.addView(status)
        preview=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER }
        display.addView(label("Оригинальный мем"))
        display.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        display.addView(label("Фото игроков и оценки появятся в следующем модуле"))
        root.addView(controls,LinearLayout.LayoutParams(0,-1,1f))
        root.addView(display,LinearLayout.LayoutParams(0,-1,2f))
        setContentView(root)
        if(folder!=null) scan()
    }
    private fun chooseFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        },100)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==100 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folder=uri; prefs.edit().putString("folder",uri.toString()).apply()
                selected=emptySet(); scan()
            } catch(e:Exception) { status.text="Ошибка доступа: ${e.message}" }
        }
    }
    private fun scan() {
        val uri=folder ?: run { status.text="Сначала выбери папку"; return }
        try {
            val treeId=DocumentsContract.getTreeDocumentId(uri)
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(uri,treeId)
            val found=mutableListOf<Uri>()
            contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { c ->
                while(c.moveToNext()) {
                    val mime=c.getString(1) ?: ""
                    if(mime.startsWith("image/")) found.add(DocumentsContract.buildDocumentUriUsingTree(uri,c.getString(0)))
                }
            }
            memes=found; selected=selected.intersect(memes.toSet())
            status.text="Мемов в папке: ${memes.size}"
        } catch(e:Exception) { status.text="Нет доступа к папке. Выбери её снова." }
    }
    private fun chooseMemes() {
        if(memes.isEmpty()) { status.text="Сначала добавь мемы в папку"; return }
        val checked=memes.map { it in selected }.toBooleanArray()
        val labels=memes.indices.map { "Мем ${it+1}" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Выбери картинки по номерам")
            .setMultiChoiceItems(labels,checked) { _,i,on -> checked[i]=on }
            .setPositiveButton("Готово") { _,_ ->
                selected=memes.indices.filter { checked[it] }.map { memes[it] }.toSet()
                status.text="Отмечено: ${selected.size}"
            }.setNegativeButton("Отмена",null).show()
    }
    private fun startGame() {
        scan()
        val r=rounds.text.toString().toIntOrNull() ?: 0
        val p=perRound.text.toString().toIntOrNull() ?: 0
        if(r !in 1..10 || p !in 1..20) { status.text="Укажи 1–10 раундов и 1–20 мемов"; return }
        val pool=if(mode.isChecked) memes.filter { it in selected } else memes
        if(pool.size < r*p) { status.text="Нужно ${r*p} мемов, доступно ${pool.size}"; return }
        order=pool.shuffled(Random.Default).take(r*p); index=0
        showMeme()
    }
    private fun nextMeme() {
        if(order.isEmpty()) { status.text="Сначала начни игру"; return }
        index++
        if(index>=order.size) { order=emptyList(); preview.setImageDrawable(null); status.text="Игра окончена"; return }
        showMeme()
    }
    private fun showMeme() {
        preview.setImageURI(order[index])
        val p=perRound.text.toString().toInt()
        status.text="Раунд ${index/p+1}, мем ${index%p+1} из $p"
    }
}
