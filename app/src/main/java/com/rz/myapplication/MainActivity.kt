package com.rz.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val FOLDER_PICKER_REQUEST_CODE = 999
    private lateinit var player: ExoPlayer
    private var selectedFolderUri: Uri? = null
    private var fileNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player = ExoPlayer.Builder(this).build()
        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = player

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener { openFolderChooser() }

        val savedUri = loadSavedFolderUri()
        savedUri?.let { loadMusicFromFolder(it) }
    }

    private fun openFolderChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FOLDER_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedFolderUri = uri
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveFolderUri(uri) // Guarda la URI
                loadMusicFromFolder(uri)
            }
        }
    }


        private fun saveFolderUri(uri: Uri) {
            val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString("SAVED_FOLDER_URI", uri.toString())
                apply()
            }
        }

        private fun loadSavedFolderUri(): Uri? {
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            val uriString = sharedPref.getString("SAVED_FOLDER_URI", null)
            return uriString?.let { Uri.parse(it) }
        }

    private fun loadMusicFromFolder(folderUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val mediaSource = ConcatenatingMediaSource()
            fileNames.clear()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri)
            )

            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idColumn)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                    val fileName = cursor.getString(nameColumn)
                    fileNames.add(fileName)

                    val mediaItem = MediaItem.fromUri(fileUri)
                    mediaSource.addMediaSource(
                        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this@MainActivity))
                            .createMediaSource(mediaItem)
                    )
                }
            }

            withContext(Dispatchers.Main) {
                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()

                player.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val currentIndex = player.currentMediaItemIndex
                        findViewById<TextView>(R.id.tvFileName).text =
                            fileNames.getOrElse(currentIndex) { "" }
                    }
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
