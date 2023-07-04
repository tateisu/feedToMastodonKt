//import org.junit.Test
//import java.io.File
//import java.io.FileInputStream
//import java.time.ZonedDateTime
//import java.time.format.DateTimeFormatter
//import kotlin.test.assertContentEquals
//import kotlin.test.assertEquals
//
//class RssToTweet {
//    companion object {
//        private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME!!
//        private fun String.toTimeMs() =
//            ZonedDateTime.parse(this, formatter).toInstant().toEpochMilli()
//    }
//
//    @Test
//    fun testToTimeMs() {
//        val timeMs = "Fri, 31 Mar 2023 03:39:40 GMT".toTimeMs()
//        assertEquals(1680233980000, timeMs, "toTimeMs")
//    }
//
//    @Test
//    fun test1() {
//        // Z:\mastodon-related\feedToMastodonKt
//        val cwd = File(".").canonicalPath
//
//        val tweets = FileInputStream("src/test/resources/testRss.xml")
//            .use { it.parseTweetRss() }
//
//        val expects = arrayOf(
//            // 0
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1641646416685830145",
//                id = "1641646416685830145",
//                timeMs = "Fri, 31 Mar 2023 03:39:40 GMT".toTimeMs(),
//                isRt = false,
//                text = "『才女のお世話』ポップアップの受注グッズの締め切りは今日までです⏳ よろしくお願いいたします✨",
//                userScreenName = "sakurapion",
//                thumbnails = emptyList(),
//                quotedUrls = emptyList(),
//            ),
//            // 1
//            Tweet(
//                statusUrl = "https://twitter.com/miku_emori/status/1641642485247266817",
//                id = "1641642485247266817",
//                timeMs = "Fri, 31 Mar 2023 03:24:03 GMT".toTimeMs(),
//                isRt = true,
//                text = "㊗#美少女京扇子 クラファン、目標達成ありがとうございます！！\uD83D\uDE2D\uD83D\uDE4F\uD83C\uDFFB✨ ただ、目標達成したからといって打ち止めではありません！ 支援が増えればその分、扇子職人さんにお仕事を持っていくことが出来ます！ 本日最終日ですが、まだまだ終わりませんよ～～～！何卒！！\uD83D\uDE47\uD83C\uDFFB\u200D♀️",
//                userScreenName = "miku_emori",
//                thumbnails = listOf(
//                    Media("http://nitter.juggler.jp/pic/media%2FFshJCopagAAkqz5.png"),
//                ),
//                quotedUrls = listOf(
//                    "https://twitter.com/melonbooks/status/1628938117804883968"
//                ),
//            ),
//            // 2
//            Tweet(
//                statusUrl = "https://twitter.com/m_okuma0831/status/1640633740354551809",
//                id = "1640633740354551809",
//                timeMs = "Tue, 28 Mar 2023 08:35:39 GMT".toTimeMs(),
//                isRt = true,
//                text = "\uD83C\uDF3A\uD83C\uDF3F✨",
//                userScreenName = "m_okuma0831",
//                thumbnails = listOf(
//                    Media("http://nitter.juggler.jp/pic/media%2FFsSyz_raYAAvBvF.jpg"),
//                ),
//                quotedUrls = emptyList(),
//            ),
//            // 3
//            Tweet(
//                statusUrl = "https://twitter.com/syakisyaki890/status/1641021567978336256",
//                id = "1641021567978336256",
//                timeMs = "Wed, 29 Mar 2023 10:16:45 GMT".toTimeMs(),
//                isRt = true,
//                text = "猫耳メイド：ねあむ ちゃん イラコンに提出したちびキャラです",
//                userScreenName = "syakisyaki890",
//                thumbnails = listOf(
//                    Media("http://nitter.juggler.jp/pic/media%2FFsYT7L6aYAIazt4.jpg"),
//                ),
//                quotedUrls = emptyList(),
//            ),
//            // 4
//            Tweet(
//                statusUrl = "https://twitter.com/hiziri_A/status/1641343645247995904",
//                id = "1641343645247995904",
//                timeMs = "Thu, 30 Mar 2023 07:36:34 GMT".toTimeMs(),
//                isRt = true,
//                text = "\uD83C\uDF38",
//                userScreenName = "hiziri_A",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsc5V91aAAIn-g4.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            //5
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1641368416203276289",
//                id = "1641368416203276289",
//                timeMs = "Thu, 30 Mar 2023 09:15:00 GMT".toTimeMs(),
//                isRt = false,
//                text = "こちらの締め切り明日の3/31の23：59までです⏲️ よろしくお願いいたします！ \uD83D\uDD3B美少女京扇子 クラファンページはこちらから http://qr.paps.jp/77qBL",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsctMGxaEAAAjLD.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 6
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1641009804520099840",
//                id = "1641009804520099840",
//                timeMs = "Wed, 29 Mar 2023 09:30:00 GMT".toTimeMs(),
//                isRt = false,
//                text = "\uD83C\uDF38",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsXnsjkakAMO6ps.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 7
//            Tweet(
//                statusUrl = "https://twitter.com/animateinfo/status/1639192839392477184",
//                id = "1639192839392477184",
//                timeMs = "Fri, 24 Mar 2023 09:10:02 GMT".toTimeMs(),
//                isRt = true,
//                text = "\uD83D\uDCE2「#美少女京扇子」クラファン追い込み企画！Wフォロー&RTキャンペーン！ 「@animateinfo」と「@miku_emori」をWフォロー&当ツイートRT！ ⇨抽選で【人気イラストレーター直筆サイン色紙】を各1名様にプレゼント！ 期間：3/31(金)23:59 ▼美少女京扇子クラファン実施中！ https://bit.ly/3lxiThH",
//                userScreenName = "animateinfo",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFr-VM1haMAIFB5b.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 8
//            Tweet(
//                statusUrl = "https://twitter.com/sirokuma_shake/status/1640673388938276865",
//                id = "1640673388938276865",
//                timeMs = "Tue, 28 Mar 2023 11:13:12 GMT".toTimeMs(),
//                isRt = true,
//                text = "「この後、どうする？」",
//                userScreenName = "sirokuma_shake",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsTXiOKaYAAFHDy.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 9
//            Tweet(
//                statusUrl = "https://twitter.com/naechama21/status/1640656131348627456",
//                id = "1640656131348627456",
//                timeMs = "Tue, 28 Mar 2023 10:04:38 GMT".toTimeMs(),
//                isRt = true,
//                text = "絶対両思いだと思っていた幼なじみに彼女が出来てしまった 通称：毒りんごちゃんの設定資料です\uD83D\uDDA4",
//                userScreenName = "naechama21",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsTHlTPaQAI1App.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 10
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1640924516791762944",
//                id = "1640924516791762944",
//                timeMs = "Wed, 29 Mar 2023 03:51:06 GMT".toTimeMs(),
//                isRt = false,
//                text = "オーバーラップ『クラなつ』玲のバレンタイングッズサンプル頂きました。 購入して頂いた皆様ありがとうございました✨",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFsW7-e1aYAIlZqq.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 11
//            Tweet(
//                statusUrl = "https://twitter.com/katura69/status/1639779200092766210",
//                id = "1639779200092766210",
//                timeMs = "Sun, 26 Mar 2023 00:00:01 GMT".toTimeMs(),
//                isRt = true,
//                text = "本日、コミカライズ版 #イケナイ教 は連載3⃣周年を迎えました。読者の皆様の応援のおかげです、ありがとうございます！ 記念に、ローソンさんで出力できるイラストを１枚描き下しました。もしよろしければお迎えくださいませ…！ ４周年目のイケナイ教もよろしくお願いいたします\uD83D\uDE47",
//                userScreenName = "katura69",
//                thumbnails = emptyList(),
//                quotedUrls = listOf("http://twitter.com/pashcomics/status/1639431911910305792#m"),
//            ),
//            // 12
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1639105127545192448",
//                id = "1639105127545192448",
//                timeMs = "Fri, 24 Mar 2023 03:21:30 GMT".toTimeMs(),
//                isRt = false,
//                text = "挿絵担当しました\uD83C\uDF38ダッシュエックス文庫【許嫁が出来たと思ったら、その許嫁が学校で有名な『悪役令嬢』だったんだけど、どうすればいい？】２巻が本日発売です\uD83C\uDF89 よろしくお願いいたします",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFr9E_bxaMAMNz3g.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 13
//            Tweet(
//                statusUrl = "https://twitter.com/sodayou00/status/1638981610296188929",
//                id = "1638981610296188929",
//                timeMs = "Thu, 23 Mar 2023 19:10:41 GMT".toTimeMs(),
//                isRt = true,
//                text = "2巻発売記念ss、投稿させて頂きました！ よろしければ是非··· https://ncode.syosetu.com/n0162fv/ #narou #narouN0162FV",
//                userScreenName = "sodayou00",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/card_img%2F1641518180769366016%2FhOvMzzj1%3Fformat%3Djpg%26name%3D600x600")),
//                quotedUrls = emptyList(),
//            ),
//            // 14
//            Tweet(
//                statusUrl = "https://twitter.com/hiziri_A/status/1638499570962538498",
//                id = "1638499570962538498",
//                timeMs = "Wed, 22 Mar 2023 11:15:14 GMT".toTimeMs(),
//                isRt = true,
//                text = "☕️ナギサ様☕️",
//                userScreenName = "hiziri_A",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFr0erN4aEAQ_5jy.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 15
//            Tweet(
//                statusUrl = "https://twitter.com/hiziri_A/status/1638025245302063105",
//                id = "1638025245302063105",
//                timeMs = "Tue, 21 Mar 2023 03:50:26 GMT".toTimeMs(),
//                isRt = true,
//                text = "何卒… #三億アカウントの中から私を発掘してください",
//                userScreenName = "hiziri_A",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFrtvRz-aYAIzxIm.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 16
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1638110702157037569",
//                id = "1638110702157037569",
//                timeMs = "Tue, 21 Mar 2023 09:30:00 GMT".toTimeMs(),
//                isRt = false,
//                text = "『許嫁が出来たと思ったら、その許嫁が学校で有名な『悪役令嬢』だったんだけど、どうすればいい? 2』 イラスト担当いたしました✨3月24日発売です！ http://www.shueisha.co.jp/books/items/contents.html?isbn=978-4-08-631501-2#&gid=null&pid=1",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFrpWgQgagAAyNUI.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 17
//            Tweet(
//                statusUrl = "https://twitter.com/sakurapion/status/1637752087558881280",
//                id = "1637752087558881280",
//                timeMs = "Mon, 20 Mar 2023 09:45:00 GMT".toTimeMs(),
//                isRt = false,
//                text = "\uD83C\uDF38",
//                userScreenName = "sakurapion",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFrpUWeVaQAA9bc5.jpg")),
//                quotedUrls = emptyList(),
//            ),
//            // 18
//            Tweet(
//                statusUrl = "https://twitter.com/melon_manga/status/1631579487497498627",
//                id = "1631579487497498627",
//                timeMs = "Fri, 03 Mar 2023 08:57:17 GMT".toTimeMs(),
//                isRt = true,
//                text = "\uD83D\uDCD5予約情報 2023/03/24 発売 #ダッシュエックス文庫 「許嫁が出来たと思ったら、その許嫁が学校で有名な『悪役令嬢』だったんだけど、どうすればいい? 2」 著：疎陀陽 イラスト：みわべさくら（@sakurapion） ✨B2タペストリー付き✨ メロンブックス限定版 \uD83D\uDD3D通販 https://www.melonbooks.co.jp/detail/detail.php?product_id=1866974",
//                userScreenName = "melon_manga",
//                thumbnails = listOf(
//                    Media("http://nitter.juggler.jp/pic/media%2FFqSIiSpaEAI8xjt.jpg"),
//                    Media("http://nitter.juggler.jp/pic/media%2FFqSIiTMagAA9Ujg.jpg"),
//                ),
//                quotedUrls = emptyList(),
//            ),
//            // 19
//            Tweet(
//                statusUrl = "https://twitter.com/sodayou00/status/1636619052272726017",
//                id = "1636619052272726017",
//                timeMs = "Fri, 17 Mar 2023 06:42:43 GMT".toTimeMs(),
//                isRt = true,
//                text = "2巻最後の仕事が始まる···サイン本だー！",
//                userScreenName = "sodayou00",
//                thumbnails = listOf(Media("http://nitter.juggler.jp/pic/media%2FFrZwWXeaQAAJce5.jpg")),
//                quotedUrls = emptyList(),
//            ),
//        )
//        assertEquals(expects.size, tweets.size, "size of tweets")
//        repeat(expects.size) {
//            val e = expects[it]
//            val a = tweets[it]
//            assertEquals(
//                e.statusUrl,
//                a.statusUrl,
//                "[$it] statusUrl"
//            )
//            assertEquals(
//                e.id,
//                a.id,
//                "[$it] id"
//            )
//            assertEquals(
//                e.pubDate,
//                a.pubDate,
//                "[$it] pubDate"
//            )
//            assertEquals(
//                e.isRt,
//                a.isRt,
//                "[$it] isRt"
//            )
//            assertEquals(
//                e.userScreenName,
//                a.userScreenName,
//                "[$it] userScreenName"
//            )
//            assertEquals(
//                e.text,
//                a.text,
//                "[$it] text"
//            )
//            assertContentEquals(
//                e.thumbnails,
//                a.thumbnails,
//                "[$it] thumbnails"
//            )
//        }
//
//    }
//}
