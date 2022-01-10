package ViewHolder

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.telecommand_bluetooth.R

class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.text_item)
    val subName : TextView = itemView.findViewById(R.id.text_subItem)
}
