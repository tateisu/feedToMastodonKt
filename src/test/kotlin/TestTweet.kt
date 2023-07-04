import org.junit.Assert.assertFalse
import org.junit.Test
import util.decodeJsonObject

class TestTweet {

    @Test
    fun testQuote() {
        val json = """{
            "id": 0,
            "location": "",
            "conversation_id_str": "1675631952089714688",
            "created_at": "Sun Jul 02 22:26:03 +0000 2023",
            "display_text_range": [
                0,
                4
            ],
            "entities": {
                "user_mentions": [],
                "urls": [],
                "hashtags": [],
                "symbols": [],
                "media": []
            },
            "favorite_count": 0,
            "favorited": false,
            "full_text": "引用RT",
            "id_str": "1675631952089714688",
            "lang": "ja",
            "permalink": "/tateisu/status/1675631952089714688",
            "possibly_sensitive": false,
            "quote_count": 0,
            "reply_count": 0,
            "retweet_count": 0,
            "retweeted": false,
            "text": "引用RT",
            "user": {
                "blocking": false,
                "created_at": "Tue May 06 15:50:15 +0000 2008",
                "default_profile": false,
                "default_profile_image": false,
                "description": "怠惰な人。fediverseにほぼ移住しました。",
                "entities": {
                    "description": {
                        "urls": []
                    },
                    "url": {
                        "urls": [
                            {
                                "display_url": "mastodon.juggler.jp/@tateisu",
                                "expanded_url": "https://mastodon.juggler.jp/@tateisu",
                                "url": "https://t.co/rSbCNws1kk",
                                "indices": [
                                    0,
                                    23
                                ]
                            }
                        ]
                    }
                },
                "fast_followers_count": 0,
                "favourites_count": 937,
                "follow_request_sent": false,
                "followed_by": false,
                "followers_count": 252,
                "following": false,
                "friends_count": 0,
                "has_custom_timelines": false,
                "id": 0,
                "id_str": "14674862",
                "is_translator": false,
                "listed_count": 21,
                "location": "",
                "media_count": 139,
                "name": "tateisu",
                "normal_followers_count": 252,
                "notifications": false,
                "profile_banner_url": "https://pbs.twimg.com/profile_banners/14674862/1397003583",
                "profile_image_url_https": "https://pbs.twimg.com/profile_images/1645849132/15409_1976824491_normal.jpg",
                "protected": false,
                "screen_name": "tateisu",
                "show_all_inline_media": false,
                "statuses_count": 5889,
                "time_zone": "",
                "translator_type": "none",
                "url": "https://t.co/rSbCNws1kk",
                "utc_offset": 0,
                "verified": false,
                "withheld_in_countries": [],
                "withheld_scope": "",
                "is_blue_verified": false
            },
            "is_quote_status": true,
            "quoted_status": {
                "id": 0,
                "location": "",
                "conversation_id_str": "1675631805003857921",
                "created_at": "Sun Jul 02 22:25:52 +0000 2023",
                "display_text_range": [
                    0,
                    5
                ],
                "entities": {
                    "user_mentions": [],
                    "urls": [],
                    "hashtags": [],
                    "symbols": [],
                    "media": []
                },
                "favorite_count": 0,
                "favorited": false,
                "full_text": "普通の返信",
                "id_str": "1675631905579102208",
                "in_reply_to_name": "tateisu",
                "in_reply_to_screen_name": "tateisu",
                "in_reply_to_status_id_str": "1675631805003857921",
                "in_reply_to_user_id_str": "14674862",
                "lang": "ja",
                "permalink": "/tateisu/status/1675631905579102208",
                "possibly_sensitive": false,
                "quote_count": 1,
                "reply_count": 0,
                "retweet_count": 0,
                "retweeted": false,
                "text": "普通の返信",
                "user": {
                    "blocking": false,
                    "created_at": "Tue May 06 15:50:15 +0000 2008",
                    "default_profile": false,
                    "default_profile_image": false,
                    "description": "怠惰な人。fediverseにほぼ移住しました。",
                    "entities": {
                        "description": {
                            "urls": []
                        },
                        "url": {
                            "urls": [
                                {
                                    "display_url": "mastodon.juggler.jp/@tateisu",
                                    "expanded_url": "https://mastodon.juggler.jp/@tateisu",
                                    "url": "https://t.co/rSbCNws1kk",
                                    "indices": [
                                        0,
                                        23
                                    ]
                                }
                            ]
                        }
                    },
                    "fast_followers_count": 0,
                    "favourites_count": 937,
                    "follow_request_sent": false,
                    "followed_by": false,
                    "followers_count": 252,
                    "following": false,
                    "friends_count": 0,
                    "has_custom_timelines": false,
                    "id": 0,
                    "id_str": "14674862",
                    "is_translator": false,
                    "listed_count": 21,
                    "location": "",
                    "media_count": 139,
                    "name": "tateisu",
                    "normal_followers_count": 252,
                    "notifications": false,
                    "profile_banner_url": "https://pbs.twimg.com/profile_banners/14674862/1397003583",
                    "profile_image_url_https": "https://pbs.twimg.com/profile_images/1645849132/15409_1976824491_normal.jpg",
                    "protected": false,
                    "screen_name": "tateisu",
                    "show_all_inline_media": false,
                    "statuses_count": 5889,
                    "time_zone": "",
                    "translator_type": "none",
                    "url": "https://t.co/rSbCNws1kk",
                    "utc_offset": 0,
                    "verified": false,
                    "withheld_in_countries": [],
                    "withheld_scope": "",
                    "is_blue_verified": false
                }
            },
            "quoted_status_id_str": "1675631905579102208",
            "quoted_status_permalink": {
                "display": "twitter.com/tateisu/status…",
                "expanded": "https://twitter.com/tateisu/status/1675631905579102208",
                "url": "https://t.co/97aX23MogZ"
            }
        }"""
        val tweet = json.decodeJsonObject().toTweet()
        assertFalse("has quoteTo", tweet.quoteTo.isNullOrEmpty())
    }

    @Test
    fun testReply() {
        val json = """{
            "id": 0,
            "location": "",
            "conversation_id_str": "1675631805003857921",
            "created_at": "Sun Jul 02 22:25:52 +0000 2023",
            "display_text_range": [
                0,
                5
            ],
            "entities": {
                "user_mentions": [],
                "urls": [],
                "hashtags": [],
                "symbols": [],
                "media": []
            },
            "favorite_count": 0,
            "favorited": false,
            "full_text": "普通の返信",
            "id_str": "1675631905579102208",
            "in_reply_to_name": "tateisu",
            "in_reply_to_screen_name": "tateisu",
            "in_reply_to_status_id_str": "1675631805003857921",
            "in_reply_to_user_id_str": "14674862",
            "lang": "ja",
            "permalink": "/tateisu/status/1675631905579102208",
            "possibly_sensitive": false,
            "quote_count": 1,
            "reply_count": 0,
            "retweet_count": 0,
            "retweeted": false,
            "text": "普通の返信",
            "user": {
                "blocking": false,
                "created_at": "Tue May 06 15:50:15 +0000 2008",
                "default_profile": false,
                "default_profile_image": false,
                "description": "怠惰な人。fediverseにほぼ移住しました。",
                "entities": {
                    "description": {
                        "urls": []
                    },
                    "url": {
                        "urls": [
                            {
                                "display_url": "mastodon.juggler.jp/@tateisu",
                                "expanded_url": "https://mastodon.juggler.jp/@tateisu",
                                "url": "https://t.co/rSbCNws1kk",
                                "indices": [
                                    0,
                                    23
                                ]
                            }
                        ]
                    }
                },
                "fast_followers_count": 0,
                "favourites_count": 937,
                "follow_request_sent": false,
                "followed_by": false,
                "followers_count": 252,
                "following": false,
                "friends_count": 0,
                "has_custom_timelines": false,
                "id": 0,
                "id_str": "14674862",
                "is_translator": false,
                "listed_count": 21,
                "location": "",
                "media_count": 139,
                "name": "tateisu",
                "normal_followers_count": 252,
                "notifications": false,
                "profile_banner_url": "https://pbs.twimg.com/profile_banners/14674862/1397003583",
                "profile_image_url_https": "https://pbs.twimg.com/profile_images/1645849132/15409_1976824491_normal.jpg",
                "protected": false,
                "screen_name": "tateisu",
                "show_all_inline_media": false,
                "statuses_count": 5889,
                "time_zone": "",
                "translator_type": "none",
                "url": "https://t.co/rSbCNws1kk",
                "utc_offset": 0,
                "verified": false,
                "withheld_in_countries": [],
                "withheld_scope": "",
                "is_blue_verified": false
            }
        }"""
        val tweet = json.decodeJsonObject().toTweet()
        assertFalse("has replyTo", tweet.replyTo.isNullOrEmpty())
    }
}