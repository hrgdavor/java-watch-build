
// requires
fs = require('fs');
path = require('path');


/***************************** initialization from command line *********************************/
var args = process.argv.slice(2);

process.stdin.resume();
process.stdin.setEncoding('utf8');
process.stdin.on('data', function(data){
	var lines = data.split('\n');
	for(var i=0; i<lines.length; i++){
		process.stdout.write('data:'+lines[i]+'\n');
	}
});

process.on('SIGINT', function () {
  process.exit(0);
});


