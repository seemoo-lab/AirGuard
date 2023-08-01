import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.settings.AttributionItem

class AttributionAdapter(
    private val attributions: List<AttributionItem>,
    private val onItemClick: (AttributionItem) -> Unit
) : RecyclerView.Adapter<AttributionAdapter.AttributionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttributionViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_attribution, parent, false)
        return AttributionViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttributionViewHolder, position: Int) {
        val attribution = attributions[position]
        holder.bind(attribution, onItemClick)
    }

    override fun getItemCount(): Int = attributions.size

    class AttributionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewAttributionName)

        fun bind(attribution: AttributionItem, onItemClick: (AttributionItem) -> Unit) {
            nameTextView.text = attribution.name
            itemView.setOnClickListener { onItemClick(attribution) }
        }
    }
}