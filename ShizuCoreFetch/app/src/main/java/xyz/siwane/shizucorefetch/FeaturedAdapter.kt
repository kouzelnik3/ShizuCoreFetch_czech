package xyz.siwane.shizucorefetch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import xyz.siwane.shizucorefetch.databinding.ItemFeaturedAppBinding

class FeaturedAdapter(
    private val featuredList: List<AppModel>,
    private val onAppClick: (AppModel) -> Unit
) : RecyclerView.Adapter<FeaturedAdapter.FeaturedViewHolder>() {

    inner class FeaturedViewHolder(val binding: ItemFeaturedAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedViewHolder {
        val binding = ItemFeaturedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeaturedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeaturedViewHolder, position: Int) {
        val appItem = featuredList[position]
        val context = holder.itemView.context

        holder.binding.tvFeaturedAppName.text = appItem.name
        
        // 💡 1. تحديد اتجاه تخطيط الجهاز (RTL للغات مثل العربية، و LTR لباقي اللغات)
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        
        // 💡 2. اختيار البانر المناسب بناءً على لغة النظام وتوفر الرابط
        val targetBannerUrl = if (isRtl && appItem.bannerUrlAr.isNotEmpty()) {
            appItem.bannerUrlAr // عرض البانر العربي إذا كان الجهاز RTL والبانر متوفر
        } else if (appItem.bannerUrl.isNotEmpty()) {
            appItem.bannerUrl // عرض البانر الافتراضي/الإنجليزي
        } else {
            appItem.iconUrl // خيار احتياطي أخير (Fallback) لتجنب المساحات الفارغة
        }

        // تحميل البانر في واجهة العرض
        holder.binding.ivFeaturedBanner.load(targetBannerUrl) {
            crossfade(true)
        }

        // إرسال النقرة للواجهة
        holder.binding.root.setOnClickListener {
            onAppClick(appItem)
        }
    }

    override fun getItemCount(): Int = featuredList.size
}
