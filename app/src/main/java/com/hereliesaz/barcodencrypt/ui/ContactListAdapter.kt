package com.hereliesaz.barcodencrypt.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hereliesaz.barcodencrypt.data.ContactWithBarcodes
import com.hereliesaz.barcodencrypt.databinding.ListItemContactBinding

/**
 * The adapter that gives form to the Scribe's records.
 * It efficiently populates a RecyclerView with the list of contacts, handling view creation
 * and data binding. It uses a [DiffUtil] callback to compute list updates efficiently.
 *
 * @param onItemClicked The ritual to perform when a contact's record is tapped.
 */
class ContactListAdapter(private val onItemClicked: (ContactWithBarcodes) -> Unit) :
    ListAdapter<ContactWithBarcodes, ContactListAdapter.ContactViewHolder>(ContactsComparator()) {

    /**
     * Creates new [RecyclerView.ViewHolder]s (invoked by the layout manager).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ListItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    /**
     * Replaces the contents of a view (invoked by the layout manager).
     */
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val current = getItem(position)
        holder.itemView.setOnClickListener {
            onItemClicked(current)
        }
        holder.bind(current)
    }

    /**
     * The ViewHolder, a single tombstone in the RecyclerView graveyard.
     * It holds the view for a single contact item.
     */
    class ContactViewHolder(private val binding: ListItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactWithBarcodes: ContactWithBarcodes) {
            binding.contactNameTextView.text = contactWithBarcodes.contact.name
            val barcodeCount = contactWithBarcodes.barcodes.size
            binding.barcodeCountTextView.text = "Barcodes: $barcodeCount"
        }
    }

    /**
     * The comparator, a cold and efficient judge.
     * It determines how to calculate differences between two lists of contacts.
     */
    class ContactsComparator : DiffUtil.ItemCallback<ContactWithBarcodes>() {
        override fun areItemsTheSame(oldItem: ContactWithBarcodes, newItem: ContactWithBarcodes): Boolean {
            return oldItem.contact.id == newItem.contact.id
        }

        override fun areContentsTheSame(oldItem: ContactWithBarcodes, newItem: ContactWithBarcodes): Boolean {
            return oldItem == newItem
        }
    }
}
