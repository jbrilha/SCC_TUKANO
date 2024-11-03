const crypto = require("crypto");
const fs = require("fs");

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
    let pword = randomPassword(15);
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
    const short = JSON.parse(response.body);

    const url = new URL(short.blobUrl);
    // console.log("\nblobUrl: " + url);

    const blobId = url.pathname.split("/").pop();
    // console.log("blobId: " + blobId);

    const token = url.searchParams.get("token");
    // console.log("token: " + token);

    context.vars.token = token;
    context.vars.blobId = blobId;

    return next();
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

module.exports = {
    randomBytes,
    uploadRandomizedUser,
    processCreateResponse,
    captureUserResponse,
    getBlobIdFromShort,
    processDownload,
    setQuery,
};
