package com.elizeu.gabarito.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.elizeu.gabarito.Corretor
import com.elizeu.gabarito.GabaritoStore
import com.elizeu.gabarito.ImageUtils
import com.elizeu.gabarito.ItemProva
import com.elizeu.gabarito.MainActivity
import com.elizeu.gabarito.databinding.FragmentScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanFragment : Fragment() {

    private var _b: FragmentScanBinding? = null
    private val b get() = _b!!
    private lateinit var photoFile: File
    private var bmp: Bitmap? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                onImageReady(photoFile)
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { onUriReady(it) }
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentScanBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnCamera.setOnClickListener {
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
        b.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        b.btnProcessar.setOnClickListener { processar() }
        b.inputValor.setText(
            GabaritoStore.loadValor(requireContext())
                .toString()
                .replace('.', ',')
        )
    }

    private fun onUriReady(uri: Uri) {
        lifecycleScope.launch {
            b.txtStatus.text = "Carregando imagem..."
            b.progress.visibility = View.VISIBLE
            val bm = withContext(Dispatchers.IO) { ImageUtils.decodeUri(requireContext(), uri) }
            b.progress.visibility = View.GONE
            if (bm == null) {
                b.txtStatus.text = "Erro ao abrir a imagem."
                return@launch
            }
            showImage(bm)
        }
    }

    private fun onImageReady(file: File) {
        lifecycleScope.launch {
            b.txtStatus.text = "Carregando imagem..."
            b.progress.visibility = View.VISIBLE
            val bm = withContext(Dispatchers.IO) { ImageUtils.decodeFile(file.absolutePath) }
            b.progress.visibility = View.GONE
            if (bm == null) {
                b.txtStatus.text = "Erro ao abrir a imagem."
                return@launch
            }
            showImage(bm)
        }
    }

    private fun showImage(bm: Bitmap) {
        bmp = bm
        b.imgPreview.setImageBitmap(bm)
        b.cardPreview.visibility = View.VISIBLE
        b.btnProcessar.isEnabled = true
        b.txtStatus.text = "Imagem carregada (${bm.width}x${bm.height}). Toque em processar."
    }

    private fun processar() {
        val bm = bmp ?: return
        val key = GabaritoStore.load(requireContext())
        if (key.isEmpty()) {
            Toast.makeText(requireContext(), "Salve o gabarito oficial primeiro.", Toast.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.popBackStack()
            return
        }
        val valorProva = b.inputValor.text?.toString()
            ?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        if (valorProva == null || valorProva <= 0.0) {
            b.txtStatus.text = "Informe quanto a prova vale (ex.: 10)."
            b.inputValor.requestFocus()
            return
        }
        GabaritoStore.saveValor(requireContext(), valorProva)
        val nx = if (b.radioEM.isChecked) 5 else 4
        b.progress.visibility = View.VISIBLE
        b.btnProcessar.isEnabled = false
        b.txtStatus.text = "Lendo bolhas..."

        lifecycleScope.launch {
            val items: List<ItemProva> = try {
                Corretor.corrigir(bm, nx)
            } catch (e: Exception) {
                emptyList()
            }
            b.progress.visibility = View.GONE
            if (items.isEmpty()) {
                b.txtStatus.text = "Não encontrei o quadro de respostas. Aproxime a câmera, use boa luz e tire outra foto."
                b.btnProcessar.isEnabled = true
                return@launch
            }
            b.txtStatus.text = "Calculando nota..."
            val args = Bundle().apply {
                putParcelableArrayList(ResultFragment.ARG_ITEMS, ArrayList(items))
                putDouble(ResultFragment.ARG_VALOR, valorProva)
            }
            (requireActivity() as MainActivity).navigate(
                ResultFragment().apply { arguments = args }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
