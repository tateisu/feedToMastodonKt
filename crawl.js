"use strict";

const puppeteer = require('puppeteer');
const fs = require('fs');
const { execSync } = require('child_process')

// ログイン情報を書いたJSON5データ
const apiSecretsFile = "/project/apiSecrets.json"

// 巡回対象URLのリスト
const crawlListFile = "/project/crawl.txt"


// HTTPリクエスト/レスポンスの保存先
const logFolder = "/project/headlessData/log";

// ブラウザのクッキーやローカルストレージなどを保持するフォルダ
const userDataDir = "/project/headlessData/browserData";

// timezone for logging
process.env.TZ = "Asia/Tokyo";

////////////////////////////////////////////////////////

// create directories
for (let dir of [logFolder, userDataDir]){
    fs.mkdir(
        dir, 
        { recursive: true }, 
        (err) => { if (err) throw err; }
    );
}

// read API secrets json.
const apiSecrets = JSON.parse(fs.readFileSync(apiSecretsFile, 'utf8'));

const crawlNames = fs.readFileSync(crawlListFile, 'utf8').split(/\n/).map(x => x.trim());

////////////////////////////////////////////////////////

// ログ出力用の日時文字列
function timeStr(date){
    const y = date.getFullYear().toString();
    const m = (date.getMonth() +1).toString().padStart( 2, '0');
    const d = date.getDate().toString().padStart( 2, '0');
    const h = date.getHours().toString().padStart( 2, '0');
    const j = date.getMinutes().toString().padStart( 2, '0');
    const s = date.getSeconds().toString().padStart( 2, '0');
    const millis = date.getMilliseconds().toString().padStart( 3, '0');
    return `${y}${m}${d}_${h}${j}${s}.${millis}`;
}

// ファイル名に使わない文字
const reFilenameNotAllowed = /[%\/\?\<\>\\\:\*\|\"\x00-\x20\x7F]/g; // "

// ファイル名のサニタイズ
function sanitizeFileName(src){
    let a = src.replaceAll(
        reFilenameNotAllowed,
        function(c){
            const s = c.charCodeAt(0).toString(16);
            if( s.length == 1){
                return "%0"+s;
            }else if( s.length == 2 ){
                return "%"+s;
            }else if( s.length == 3 ){
                return "%U0"+s;
            }else if( s.length == 4 ){
                return "%U"+s;
            }else{
                return c;
            }
        }
    );
    if(a.length >254){
        a = a.substring(0,254);
    }
    return a;
}

// ファイル名の長さを制限する
function ellipsizeFileName(src){
    if(src.length >200){
        return src.substring(0,254);
    }else{
        return src;
    }
}

// contentType から拡張子を取得する。またはnullを返す。
function guessExt(contentType){
    if(contentType){
        contentType = contentType.replace(/;.*/,"");
        if(contentType == "image/png") return "png";
        if(contentType == "image/jpeg") return "jpg";
        if(contentType == "image/gif") return "gif";
        if(contentType == "image/svg+xml") return "svg";

        if(contentType == "video/mp4") return "mp4";

        if(contentType == "text/html") return "html";
        if(contentType == "application/json") return "json";
        if(contentType == "application/javascript") return "js";
        if(contentType == "application/x-www-form-urlencoded") return "form";

        console.log(`unknown content type ${contentType}`);
    }
    return null;
}

// save http message body
let bodyFileSeed = 0;
function saveBody(body,data,caption){
    if( !body || !body.length ) return;

    const ext = guessExt(data.headers["content-type"]) ?? "bin";

    const bodyFile = `${data.time}-${++bodyFileSeed}-${caption}.${ext}`;
    fs.writeFile(`${logFolder}/${bodyFile}`, body, function(){});
    data.bodyFile = bodyFile;
}

function dumpData(dst,data){
    for(let key of [
        "time", 
        "status", "statusText",
        "method", "url", "headers",
        "bodyFile"
    ]){
        const value = data[key];
        if(!value) continue;
        if(key =="headers"){
            for (const [hk, hv] of Object.entries(value)) {
                dst.push(`- ${hk}: ${hv}`);
            }
        }else{
             dst.push(`${key}: ${value}`);
        }
    }
}

const urlsNoLog = [
    '/DataSaverMode',
    '/GetUserClaims',
    '/ListPins',
    '/ProfileSpotlightsQuery',
    '/getAltTextPromptPreference',
    'https://abs.twimg.com/responsive-web/client-web/',
    'https://api.twitter.com/1.1/account/settings.json',
    'https://api.twitter.com/live_pipeline/events',
    'https://twitter.com/i/api/1.1/friends/following/list.json',
    'https://twitter.com/i/api/1.1/hashflags.json',
    'https://twitter.com/i/api/1.1/jot/error_log.json',
    'https://twitter.com/i/api/2/badge_count/badge_count.json',
    'https://twitter.com/i/api/2/notifications/all.json',
    'https://twitter.com/i/api/fleets/v1/avatar_content',
    'https://www.google-analytics.com/analytics.js',
    'https://abs.twimg.com/sticky/animations/',
    'https://syndication.twitter.com/i/jot',
    'https://abs-0.twimg.com/emoji/v2/svg/',
    'https://api.twitter.com/1.1/live_pipeline/update_subscriptions',
];

// map from url to request data
const requestMap = {};

// puppeteer.httprequest の内容をファイルに保存する
// https://pptr.dev/api/puppeteer.httprequest
function logRequest(request){
    let time = timeStr(new Date());
    let url = request.url();
    if( urlsNoLog.find(k => url.includes(k)) ) return;
    if( url.endsWith(".js")) return;

    const data = {
        "time" : time,
        "method" : request.method(),
        "url" : url,
        "headers" : request.headers(),
    };

    saveBody(request.postData(), data, "requestBody");
    requestMap[url] = data;
}

// puppeteer.httpresponse の内容をファイルに保存する
// https://pptr.dev/api/puppeteer.httpresponse
async function logResponse(response){
    let time = timeStr(new Date());
    let url = response.url();
    if( urlsNoLog.find(k => url.includes(k)) ) return;
    if( url.endsWith(".js")) return;

    let request = requestMap[url];
    if(request==null){
        console.log(`missing request for ${url}`);
        return;
    }

    let data = {
        "time" : time,
        "status" : response.status(),
        "statusText" : response.statusText(),
        "url" : url,
        "headers" : response.headers(),
        // missing method in puppeteer.httpresponse
    };
    console.log(`${time} response ${data.status} ${data.url}`);

    (async () => {
        const contentType = data.headers["content-type"];
        if(contentType && data.status < 300 ){
            try{
                saveBody(await response.buffer(), data, "responseBody");
            }catch(e){
                if( e.toString().includes("No data found for resource with given identifier") ){
                    // no log
                }else{
                    console.log(e);
                }
            }
        }
        let lines = [];
        lines.push("##########################");
        lines.push("# request");
        dumpData(lines,request);
        lines.push("##########################");
        lines.push("# response");
        dumpData(lines,data);
        const text = lines.join("\n");
        // 
        let urlSafe = sanitizeFileName(url);
        let ext = guessExt(data.headers["content-type"]);
        if(ext==null){
            ext = "log";
        }else{
            ext += ".log";
        }
        let path = ellipsizeFileName(
            `${logFolder}/${time}-${data.status}-${request.method}-${urlSafe}`
        )+`.${ext}`;
        fs.writeFile(path, text, 'utf8', function(){});
    })();
}

let rateReset = 0;
const reHomeTimeline = new RegExp("^https://twitter.com/i/api/graphql/[^/]+/HomeTimeline");

// request/response のイベント処理を登録する
async function setLoggingInterceptor(page){
    // capture request
    page.on('request', request => {
        if (request.isInterceptResolutionHandled()) return;
        logRequest(request);
        if( request.url().match( reHomeTimeline) ){
            request.abort();
        }else{
            request.continue();
        }
    });

    // capture background response
    page.on('response', response => {
        logResponse(response);
        if( response.status() == 429 ){
            console.log(`HTTP 429 detected. ${response.url()} ${JSON.stringify(response.headers())}`);
            rateReset = response.headers()["x-rate-limit-reset"];
        }
    });
}

/////////////////////////////////////////////////

// 試験ページを読む
async function loadTestPage(page){
    await page.goto('https://juggler.jp/');
    await page.waitForNetworkIdle({ idleTime: 1500 });
}

// ログイン済みなら真
async function checkTwLogin(page){
    console.log("checkTwLogin");
    await page.goto('https://twitter.com/');
    await page.waitForNetworkIdle({ idleTime: 1500 });
    const text = await page.$eval("*", (el) => el.innerText);
    if ( text.includes("Sign in to Twitter") &&
         text.includes("Sign in with Apple") &&
         text.includes("Phone, email, or username") &&
         text.includes("Don't have an account?") 
    ) {
        console.log("not logged in.");
        return false;
    }else if(
        text.includes("Home") &&
        text.includes("For you") &&
        text.includes("Following") &&
        text.includes("See new Tweets") && 
        text.includes("What is happening?!")
    ){
        console.log("logged in.");
        return true;
    }else{
        console.log(text);
        throw "checkTwLogin: can't find signiture.";
    }
}

// https://gist.github.com/rash0/74677d56eda8233a02d182b8947c2520
async function twLogin(page){
    console.log("twLogin");
    const twLoginHandle = apiSecrets.twLoginHandle;
    const twLoginMail = apiSecrets.twLoginMail;
    const twLoginPassword = apiSecrets.twLoginPassword;

    await page.goto('https://twitter.com/i/flow/login');
    await page.waitForNetworkIdle({ idleTime: 1500 });

    /////////////////////////////////////////////////////////////
    await page.waitForSelector("[autocomplete=username]");
    await page.type("input[autocomplete=username]", twLoginMail, { delay: 50 });
    // Press the Next button
    await page.evaluate(() =>
        document.querySelectorAll('div[role="button"]')[2].click()
    );
    await page.waitForNetworkIdle({ idleTime: 1500 });

    ///////////////////////////////////////////////////////////////////////////////////
    // Sometimes twitter suspect suspicious activties, so it ask for your handle/phone Number
    const extractedText = await page.$eval("*", (el) => el.innerText);
    if (extractedText.includes("Enter your phone number or username")) {
        await page.waitForSelector("[autocomplete=on]");
        await page.type("input[autocomplete=on]", twLoginHandle, { delay: 50 });
        await page.evaluate(() =>
            document.querySelectorAll('div[role="button"]')[1].click()
        );
        await page.waitForNetworkIdle({ idleTime: 1500 });
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // Select the password input
    await page.waitForSelector('[autocomplete="current-password"]');
    await page.type('[autocomplete="current-password"]', twLoginPassword, { delay: 50 });
    // Press the Login button
    await page.evaluate(() =>
        document.querySelectorAll('div[role="button"]')[2].click()
    );
    await page.waitForNetworkIdle({ idleTime: 2000 });
}

async function main(){
    console.log(`${timeStr(new Date())} program started.`);

    execSync(`rm -f ${userDataDir}/SingletonLock`);

    console.log('create browser...');
    const browser = await puppeteer.launch({
        "headless": "new",
        "userDataDir": userDataDir,
    });

    try{
        const page = await browser.newPage();

        await page.setRequestInterception(true);
        await setLoggingInterceptor(page);

        await loadTestPage(page);


        // ログインを試みる
        let tryRemain = 3;
        while(tryRemain--){
            if(await checkTwLogin(page) ) break;
            if(!tryRemain){
                throw "ログイン試行はすべて失敗しました";
            }
            await twLogin(page);
        }
        for( let name of crawlNames ){
            if(!name) continue;
            let url = `https://twitter.com/${name}`;
            console.log(`### crawl ${url}`);
            rateReset = 0;
            tryRemain = 3;
            while(tryRemain--){
                await page.goto(url);
                await page.waitForNetworkIdle({ idleTime: 1500 });
                if( !rateReset ) break;
                let expire = parseInt(rateReset,10) * 1000;
                let remain = expire - (new Date()).getTime();
                console.log(`429 detected. rate limit reset remain=${remain/1000}sec. expire=${timeStr(new Date(expire))}`);
                if(!tryRemain){
                    throw "リトライはすべて失敗しました";
                }else{
                    await new Promise(s => setTimeout(s, remain+3000));
                }
            }
        }
        console.log(`${timeStr(new Date())} program done.`);
    }finally{
        await browser.close();
    }
}

main();
