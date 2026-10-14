package com.example.finalproject_demo.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject_demo.net.CallLimits
import com.example.finalproject_demo.ui.AssetImage
import com.example.finalproject_demo.ui.FeltCoral
import com.example.finalproject_demo.ui.FeltMustard
import com.example.finalproject_demo.ui.FeltTeal
import com.example.finalproject_demo.ui.FeltWhite
import com.example.finalproject_demo.ui.InkBrown
import com.example.finalproject_demo.ui.InkSoft
import com.example.finalproject_demo.ui.ParentText
import com.example.finalproject_demo.ui.Wool
import com.example.finalproject_demo.ui.felt
import com.example.finalproject_demo.ui.noRippleClickable

/**
 * 별 구매하기 (10-13 종훈 · #445) — 책 한 권이 곧 비용이고, 별이 그걸 사는 재화다. 수익 모델은 「구독 + 별 추가 구매」.
 * 무료 별은 설치할 때 주는 것뿐이고(10-14 종훈 · `CallLimits.WELCOME_STARS`), 그 뒤 무료로 받는 길은 「광고 보고 별 받기」다.
 *
 * **결제도 광고도 아직 붙지 않았다.** 화면은 실제 서비스처럼 다 두고, 버튼을 누르면 「준비 중입니다」가 뜬다.
 *
 * 아이에게 결제가 보이지 않게 (애플 아동 카테고리 안내 · 구글 플레이 가족 정책 · 10-13 조사):
 * 금액과 구매 버튼은 **부모 비밀번호 뒤에만** 있다. 방의 「+」는 [Sheet.STAR_GATE] 를 열 뿐이고, 닫으면 아무 말 없이 방으로 돌아간다.
 * 오또는 별을 사 달라는 말을 하지 않는다. 별 개수와 실제 금액을 늘 같이 적는다(재화와 돈을 섞지 않는다).
 * 광고도 같은 자리에만 있다 — 아이 화면에는 광고도 「광고 보기」 버튼도 없다.
 */
data class StarOffer(val key: String, val title: String, val detail: String, val price: String)

/** ⚠️ 금액 · 개수는 화면을 채우려고 둔 값이다 — 종훈이 정하면 여기 한 곳만 고친다 (책 한 권 원가 약 150원 · `CallLimits`) */
val STAR_SUBSCRIPTION = StarOffer("sub", "오또 구독", "매달 별 30개 · 언제든 해지할 수 있어요", "월 14,900원")
val STAR_AD = StarOffer("ad", "광고 보고 별 받기", "광고 한 편에 별 1개", "무료")
val STAR_PACKS = listOf(
    StarOffer("pack5", "별 5개", "책 다섯 권", "2,900원"),
    StarOffer("pack12", "별 12개", "책 열두 권", "5,900원"),
    StarOffer("pack30", "별 30개", "책 서른 권", "12,900원"),
)

@Composable
fun StarShopSheet() {
    val close = { Shell.sheet = Sheet.NONE }
    if (Shell.sheet == Sheet.STAR_GATE) {
        ObFrame(
            step = null, title = "어른 확인", sub = "별 구매는 보호자만 할 수 있어요.",
            onBack = close,
            art = { AssetImage("pi_lock", Modifier.size(160.dp)) { Text("🔒", fontSize = 60.sp) } },
            cta2 = "닫기", onCta2 = close,
        ) {
            if (Shell.hasPin) PinPad("부모 비밀번호", "3번 틀리면 30초 기다려요", onDone = { Shell.checkPin(it).also { ok -> if (ok) Shell.sheet = Sheet.STAR_SHOP } })
            else YearPad(onPass = { Shell.sheet = Sheet.STAR_SHOP })
        }
        return
    }
    var soon by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        ObFrame(
            step = null, title = "별 구매하기", sub = "별 하나로 책 한 권을 만들어요.",
            onBack = close, scroll = true,
            art = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp).felt(FeltMustard, CircleShape, lift = 4.dp), contentAlignment = Alignment.Center) { Text("⭐", fontSize = 44.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text("지금 가진 별 ${CallLimits.starsLeft()}개", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBrown)
                }
            },
        ) {
            ShopLabel("무료로 받기")
            OfferRow(STAR_AD, "광고 보기", strong = false) { soon = true }
            Spacer(Modifier.height(14.dp))
            ShopLabel("구독")
            OfferRow(STAR_SUBSCRIPTION, "구독하기", strong = true) { soon = true }
            Spacer(Modifier.height(14.dp))
            ShopLabel("별만 더 사기")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STAR_PACKS.forEach { OfferRow(it, "구매하기", strong = false) { soon = true } }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "받은 별과 산 별은 쓸 때까지 남아요.\n아이가 하루에 쓸 수 있는 별은 부모 설정에서 정할 수 있어요.",
                fontSize = 11.sp, color = InkSoft, lineHeight = 16.sp,
            )
        }
        if (soon) Box(Modifier.fillMaxSize().background(InkBrown.copy(alpha = 0.5f)).noRippleClickable { }, contentAlignment = Alignment.Center) {
            ParentText {
                Column(
                    Modifier.width(320.dp).felt(Wool, RoundedCornerShape(24.dp), lift = 10.dp, texture = false).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("준비 중입니다", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = InkBrown)
                    Spacer(Modifier.height(8.dp))
                    Text("곧 열려요. 지금은 별이 늘지 않아요.", fontSize = 13.sp, color = InkSoft)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(FeltTeal).clickable { soon = false },
                        contentAlignment = Alignment.Center,
                    ) { Text("확인", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FeltWhite) }
                }
            }
        }
    }
}

@Composable
private fun ShopLabel(t: String) {
    Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkBrown, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun OfferRow(o: StarOffer, action: String, strong: Boolean, onBuy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FeltWhite)
            .border(1.5.dp, if (strong) FeltCoral else InkBrown.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkBrown)
            Text(o.detail, fontSize = 11.sp, color = InkSoft)
        }
        Text(o.price, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkBrown)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (strong) FeltCoral else FeltTeal).clickable { onBuy() }.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FeltWhite) }
    }
}
