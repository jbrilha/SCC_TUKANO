function randomUsername(char_limit) {
    const letters = "abcdefghijklmnopqrstuvwxyz";
    let username = '';
    let num_chars = Math.floor(Math.random() * char_limit);

    for(let i = 0; i < num_chars; i++) {
        username += letters[Math.floor(Math.random() * letters.length)];
    }

    return username;
}

function randomPassword(pass_len){
    const skip_value = 33;
    const lim_values = 94;

    let password = '';
    let num_chars = Math.floor(Math.random() * pass_len);
    for (let i = 0; i < pass_len; i++) {
        let chosen_char =  Math.floor(Math.random() * lim_values) + skip_value;
        if (chosen_char == "'" || chosen_char == '"')
            i -= 1;
        else
            password += chosen_char
    }
    return password;
}

function uploadRandomizedUser(requestParams, context, ee, next) {
    let username = randomUsername(10);
    let pword = randomPassword(15);
    let email = username + "@campus.fct.unl.pt";
    let displayName = username.toUpperCase();
    
    const user = {
        id: username,
        pwd: pword,
        email: email,
        displayName: displayName
    };
    requestParams.body = JSON.stringify(user);
    return next();
}

registeredUsers = [];

function processRegisterReply(requestParams, response, context, ee, next) {
    if( typeof response.body !== 'undefined' && response.body.length > 0) {
        registeredUsers.push(response.body);
    }
    return next();
}

module.exports = {
    uploadRandomizedUser,
    processRegisterReply
};
