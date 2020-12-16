# feedToMastodonKt
TwitterからMastodonやDiscordに転載するボットスクリプトです。

### 使い方
cron から定期的に呼び出す感じです。
```
*/5 * * * * cd /path/to/feedToMastodon && java -Dfile.encoding=UTF-8 -jar feedToMastodon.jar
```

### 動作環境
```
$ cat /etc/issue
Ubuntu 16.04.6 LTS \n \l

$ java -version
openjdk version "1.8.0_275"
OpenJDK Runtime Environment (build 1.8.0_275-8u275-b01-0ubuntu1~16.04-b01)
OpenJDK 64-Bit Server VM (build 25.275-b01, mixed mode)
```

### コマンドラインオプション

標準出力などの文字コードは `java -Dfile.encoding=UTF-8` で調節します。

アプリが認識するオプションは以下の通りです。

```
Usage: feedToMastodonKt options_list
Options:
    --config, -c [./config.txt] -> config file { String }
    --dryRun [false] -> don't post the data. just read tweets.
    --verbose, -v [false] -> show verbose information
    --verboseUrlRemove [false] -> show verbose about removing urls in tweet.
    --debugMedia [false] -> post the media even if dry-run is specified.
    --read, -r [10] -> count of tweets read from server { Int }
    --dontSkipOld [false] -> don't skip tweets that is too old or already processed.
    --help, -h -> Usage info
```

### 設定ファイル
`cp config.txt.sample config.txt` してから適当に編集してください。
アクセストークンなどを記載するため。設定ファイルのパーミッションは適当に絞っておくとよいでしょう。

