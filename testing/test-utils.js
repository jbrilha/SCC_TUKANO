const crypto = require("crypto");
const fs = require("fs");

module.exports = {
    processFeed,
    randomBytes,
    storeShort,
    storeUser,
    uploadRandomizedUser,
    processCreateResponse,
    captureUserResponse,
    getBlobIdFromShort,
    processDownload,
    setQuery,
    beforeReq,
    reloadShorts,
};

function randomUsername(char_limit) {
    const letters = "abcdefghijklmnopqrstuvwxyz";
    let username = "";
    let num_chars = Math.floor(Math.random() * char_limit);

    for (let i = 0; i < num_chars; i++) {
        username += letters[Math.floor(Math.random() * letters.length)];
    }

    return username;
}

function randomPassword(pass_len) {
    const skip_value = 33;
    const lim_values = 94;

    let password = "";
    let num_chars = Math.floor(Math.random() * pass_len);
    for (let i = 0; i < pass_len; i++) {
        let chosen_char = Math.floor(Math.random() * lim_values) + skip_value;
        if (chosen_char == "'" || chosen_char == '"') i -= 1;
        else password += chosen_char;
    }
    return password;
}

function uploadRandomizedUser(requestParams, context, ee, next) {
    let username = randomUsername(10);
    // let pword = randomPassword(15);
    let pword = "password";
    let email = username + "@campus.fct.unl.pt";
    let displayName = username.toUpperCase();

    context.vars.randomPwd = pword;

    const user = {
        id: username,
        pwd: pword,
        email: email,
        displayName: displayName,
    };
    requestParams.body = JSON.stringify(user);
    return next();
}

registeredUsers = [];

function processCreateResponse(requestParams, response, context, ee, next) {
    if (typeof response.body !== "undefined" && response.body.length > 0) {
        registeredUsers.push(response.body);
    }
    return next();
}

function setQuery(requestParams, context, ee, next) {
    let query = "f";

    context.vars.query = query;

    console.log("Set query: " + context.vars.query);

    return next();
}

function captureUserResponse(requestParams, response, context, ee, next) {
    context.vars.userId = response.body;
    return next();
}

function getBlobIdFromShort(requestParams, response, context, ee, next) {
    const body = response.body;
    if (body == null || body == "") return next();

    try {
        const short = JSON.parse(body);

        const url = new URL(short.blobUrl);
        // console.log("\nblobUrl: " + url);

        const blobId = url.pathname.split("/").pop();
        // console.log("blobId: " + blobId);

        const token = url.searchParams.get("token");
        // console.log("token: " + token);

        context.vars.token = token;
        context.vars.blobId = blobId;

        return next();
    } catch (e) {
        return next();
    }
}

function randomBytes(requestParams, context, ee, next) {
    const randBytes = crypto.randomBytes(100);

    requestParams.body = randBytes;

    return next();
}

function processDownload(requestParams, response, context, ee, next) {
    const blobBytes = response.body;
    const blobId = context.vars.blobId;

    fs.writeFileSync("blobs/" + blobId, blobBytes);
    console.log("Downloaded blob: " + blobId);

    return next();
}

function processFeed(requestParams, response, context, ee, next) {
    if (!response.body) {
        console.error("PF: Empty response body");
        context.vars.feedShort = "1";
        return next();
    }

    try {
    const feed = JSON.parse(response.body);

    if (!Array.isArray(feed)) {
            console.error("feed not an array");
            return next();
        }
    if (feed.length === 0) {
            console.error("feed is empty");
            return next();
        }


    var random = Math.floor(Math.random() * feed.length)
    context.vars['feedShort'] = feed[random];

    return next();
    } catch (error) {
        console.error("Error reloading shorts:", error);
        return next();
    }
}

function beforeReq(requestParams, context, ee, next) {
    console.log("Context vars:", context.vars); // This will show all variables
    console.log("Users data:", context.vars.$users); // This shows the current CSV row
    console.log("URL being called:", requestParams.url); // This shows the final URL
    return next();
}

function storeShort(requestParams, response, context, ee, next) {
    if (!response.body) {
        console.error("SS: Empty response body");
        return next();
    }
    const short = JSON.parse(response.body);
    const shortId = short.id;

    fs.appendFileSync("data/shorts.csv", shortId + "\n");

    return next();
}

function storeUser(requestParams, response, context, ee, next) {
    if (!response.body) {
        console.error("SU: Empty response body");
        return next();
    }
    const userId = response.body;

    fs.appendFileSync("data/randUsers.csv", userId + "\n");

    return next();
}

function reloadShorts(requestParams, context, ee, next) {
    try {
        const shorts = fs
            .readFileSync("../data/shorts.csv", "utf8")
            .split("\n")
            .filter((line) => line.trim() !== "");

        shorts.shift();

        context.vars.shortId = shorts[0];

        console.log("Reloaded shorts:", shorts);
        console.log("Selected shortId:", context.vars.shortId);
    } catch (error) {
        console.error("Error reloading shorts:", error);
    }

    return next();
}
