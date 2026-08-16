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
            val existingBook = if (book.isbn != null) {
                repository.findByIsbn(book.isbn)
            } else {
                repository.findByTitleAndAuthor(book.title, book.author)
            }
            
            val isSaved = existingBook != null
            val inWishlist = existingBook?.isInWishlist == true
            
            // Initial visibility state
            binding.detailAddBtn.visibility = View.GONE
            binding.detailWishlistBtn.visibility = View.GONE
            binding.detailRemoveBtn.visibility = View.GONE
            binding.detailRemoveWishlistBtn.visibility = View.GONE
            binding.detailMoveCollectionBtn.visibility = View.GONE

            if (isSaved) {
                if (inWishlist) {
                    binding.detailMoveCollectionBtn.visibility = View.VISIBLE
                    binding.detailRemoveWishlistBtn.visibility = View.VISIBLE
                } else {
                    binding.detailRemoveBtn.visibility = View.VISIBLE
                }
            } else {
                binding.detailAddBtn.visibility = View.VISIBLE
                binding.detailWishlistBtn.visibility = View.VISIBLE
            }

            binding.detailAddBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.addBook(book, isInWishlist = false)
                        Toast.makeText(requireContext(), "Added to Collection", Toast.LENGTH_SHORT).show()
                        onCollectionChanged?.invoke()
                        dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error adding book", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.detailWishlistBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.addBook(book, isInWishlist = true)
                        Toast.makeText(requireContext(), "Added to Wishlist", Toast.LENGTH_SHORT).show()
                        onCollectionChanged?.invoke()
                        dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error adding to wishlist", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.detailRemoveBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.removeBook(existingBook?.id ?: book.id)
                        Toast.makeText(requireContext(), "Removed from Collection", Toast.LENGTH_SHORT).show()
                        onCollectionChanged?.invoke()
                        dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error removing book", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.detailRemoveWishlistBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.removeBook(existingBook?.id ?: book.id)
                        Toast.makeText(requireContext(), "Removed from Wishlist", Toast.LENGTH_SHORT).show()
                        onCollectionChanged?.invoke()
                        dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error removing from wishlist", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.detailMoveCollectionBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.updateWishlistStatus(existingBook?.id ?: book.id, false)
                        Toast.makeText(requireContext(), "Moved to Collection", Toast.LENGTH_SHORT).show()
                        onCollectionChanged?.invoke()
                        dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error moving book", Toast.LENGTH_SHORT).show()
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
