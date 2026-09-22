package com.elizeu.gabarito.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.elizeu.gabarito.Corretor
import com.elizeu.gabarito.GabaritoStore
import com.elizeu.gabarito.ImageUtils
import com.elizeu.gabarito.KeyParser
import com.elizeu.gabarito.R
import com.elizeu.gabarito.TipoGabarito
import com.elizeu.gabarito.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var photoFile: File

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                processKeyPhoto(photoFile)
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processKeyUri(it) }
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshKey()
        b.btnSaveKey.setOnClickListener { saveKey() }
        b.btnScanKey.setOnClickListener { startKeyScan() }
        b.btnCorrigir.setOnClickListener {
            (requireActivity() as com.elizeu.gabarito.MainActivity).navigate(ScanFragment())
        }
        b.btnGeradorProvas.setOnClickListener { openGeradorProvas() }
    }

    private fun openGeradorProvas() {
        val url = "https://elizeubarbosa.com.br/ferramentas/gerador-de-provas.html"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun refreshKey() {
        val key = GabaritoStore.load(requireContext())
        b.txtSavedKey.text = if (key.isEmpty()) {
            "Nenhum gabarito salvo."
        } else {
            "${key.size} questões: ${KeyParser.toDisplay(key)}"
        }
    }

    private fun saveKey() {
        val key = KeyParser.parse(b.inputKey.text?.toString() ?: "")
        if (!KeyParser.isValid(key)) {
            status("Formato inválido. Use algo como: 1E, 2D, 3C, 4A, 5B", isError = true)
            return
        }
        GabaritoStore.save(requireContext(), key)
        GabaritoStore.saveTipo(requireContext(), TipoGabarito.fromKey(key))
        status("Gabarito salvo (${key.size} questões).", isError = false)
        refreshKey()
    }

    private fun startKeyScan() {
        try {
            photoFile = ImageUtils.createCameraFile(requireContext())
            val uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                photoFile
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            takePicture.launch(intent)
        } catch (_: Exception) {
            pickImage.launch("image/*")
        }
    }

    private fun processKeyPhoto(file: File) {
        lifecycleScope.launch {
            status("Lendo gabarito da foto...")
            val bmp = withContext(Dispatchers.IO) {
                ImageUtils.decodeFile(file.absolutePath)
            }
            if (bmp == null) {
                status("Erro ao abrir a imagem.", isError = true)
                return@launch
            }
            val key = Corretor.lerGabaritoDaFoto(bmp)
            if (key.isEmpty()) {
                status("Não consegui ler o gabarito da foto. Aproxime e tente novamente.", isError = true)
                return@launch
            }
            GabaritoStore.save(requireContext(), key)
            b.inputKey.setText(KeyParser.toDisplay(key))
            refreshKey()
            status("Gabarito lido: ${key.size} questões.", isError = false)
        }
    }

    private fun processKeyUri(uri: Uri) {
        lifecycleScope.launch {
            status("Lendo gabarito da foto...")
            val bmp = withContext(Dispatchers.IO) {
                ImageUtils.decodeUri(requireContext(), uri)
            }
            if (bmp == null) {
                status("Erro ao abrir a imagem.", isError = true)
                return@launch
            }
            val key = Corretor.lerGabaritoDaFoto(bmp)
            if (key.isEmpty()) {
                status("Não consegui ler o gabarito da foto. Aproxime e tente novamente.", isError = true)
                return@launch
            }
            GabaritoStore.save(requireContext(), key)
            b.inputKey.setText(KeyParser.toDisplay(key))
            refreshKey()
            status("Gabarito lido: ${key.size} questões.", isError = false)
        }
    }

    private fun status(msg: String, isError: Boolean = false) {
        b.txtKeyStatus.text = msg
        b.txtKeyStatus.visibility = View.VISIBLE
        b.txtKeyStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isError) R.color.wrong else R.color.correct
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
