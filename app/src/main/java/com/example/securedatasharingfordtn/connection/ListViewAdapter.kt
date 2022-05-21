package com.example.securedatasharingfordtn.connection
import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.securedatasharingfordtn.R


class ListViewAdapter(
    context: Context, layoutResourceId: Int, data: ArrayList<ImageListItem>?
):
    ArrayAdapter<Any?>(context, layoutResourceId, data as List<ImageListItem>) {

    var layoutResourceId = layoutResourceId
    var data: ArrayList<ImageListItem> = data!!

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var row: View? = convertView
        var holder: ViewHolder? = null
        if (row == null) {
            val inflater = LayoutInflater.from(context) // (context as Activity).layoutInflater
            row = inflater.inflate(layoutResourceId, parent, false)
            holder = ViewHolder()
            holder.title = row.findViewById(R.id.list_title)
            holder.caption = row.findViewById(R.id.list_caption)
            holder.similarity = row.findViewById(R.id.list_similarity)
            holder.image = row.findViewById(R.id.list_image) as ImageView
            row.tag = holder
        } else {
            holder = row.tag as ViewHolder
        }

        val item: ImageListItem = data[position]
        holder!!.title?.text = "Photo ID:\t" + item.imageid
        holder!!.caption?.text = "Caption:\t" + item.caption
        holder!!.similarity?.text = "Similarity:\t" + item.similarity.toString()
        holder.image?.setImageBitmap(item.image)
        //folder = item.folder
        return row as View
    }

    internal class ViewHolder {
        var image: ImageView? = null
        var title: TextView? = null
        var caption: TextView? = null
        var similarity: TextView? = null
        //var folder: String? = null
    }
}