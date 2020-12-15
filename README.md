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
-c file または --config file   設定ファイルを指定します。デフォルトは "./config.txt" です。
-d または --debug デバッグフラグを有効にします。設定すると投稿は行われません。
```

### 設定ファイル
`cp config.txt.sample config.txt` してから適当に編集してください。
アクセストークンなどを記載するため。設定ファイルのパーミッションは適当に絞っておくとよいでしょう。

