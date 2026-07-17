package com.booklog.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.booklog.inventory.databinding.BottomSheetBookDetailBinding
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class BookDetailBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetBookDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: BookRepository
    var onCollectionChanged: (() -> Unit)? = null

    companion object {
        private const val ARG_BOOK = "arg_book"

        fun newInstance(book: Book): BookDetailBottomSheet {
            val fragment = BookDetailBottomSheet()
            val args = Bundle()
            args.putParcelable(ARG_BOOK, book)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = BookRepository(requireContext())
        
        // Force the bottom sheet to be fully expanded
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        @Suppress("DEPRECATION")
        val book = arguments?.getParcelable<Book>(ARG_BOOK) ?: return

        binding.detailTitle.text = book.title
        binding.detailAuthor.text = book.author
        binding.detailDescription.text = book.description ?: "No description available"
        Glide.with(this).load(book.thumbnail).into(binding.detailCover)

        viewLifecycleOwner.lifecycleScope.launch {
            val isAlreadySaved = repository.isBookSaved(book.id, book.isbn, book.title, book.author)
            if (isAlreadySaved) {
                binding.detailAddBtn.visibility = android.view.View.GONE
                binding.detailRemoveBtn.visibility = android.view.View.VISIBLE
                binding.detailRemoveBtn.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val existing = if (book.isbn != null) {
                                repository.findByIsbn(book.isbn)
                            } else {
                                repository.findByTitleAndAuthor(book.title, book.author)
                            }
                            val idToRemove = existing?.id ?: book.id
                            repository.removeBook(idToRemove)
                            Toast.makeText(requireContext(), "Removed from Collection", Toast.LENGTH_SHORT).show()
                            onCollectionChanged?.invoke()
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Error removing book", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                binding.detailAddBtn.visibility = android.view.View.VISIBLE
                binding.detailAddBtn.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            repository.addBook(book)
                            Toast.makeText(requireContext(), "Added to Collection", Toast.LENGTH_SHORT).show()
                            onCollectionChanged?.invoke()
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Error adding book", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
