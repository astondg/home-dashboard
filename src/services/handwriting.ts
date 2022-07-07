function HandwritingRecogniser (
  trace: any[],
  options: { width?: number, height?: number, language?: string, numOfWords?: number, numOfReturn?: number } = {},
  callback: (results?: string[], error?: Error) => void
) {
  var data = JSON.stringify({
      "api_level": "527.36",
      "input_type": "0",
      "itc": "en-t-i0-handwrit",
      "options": "enable_pre_space",
      "requests": [{
          "writing_guide": {
              "writing_area_width": options.width,
              "writing_area_height": options.height
          },
          "ink": trace,
          "language": "en",
          "pre-context": ""
      }]
  });
  var xhr = new XMLHttpRequest();
  xhr.addEventListener("readystatechange", function() {
      if (this.readyState === 4) {
          switch (this.status) {
              case 200:
                  var response = JSON.parse(this.responseText);
                  var results;
                  if (response.length === 1) callback(undefined, new Error(response[0]));
                  else results = response[1][0][1];
                  if (!!options.numOfWords) {
                      results = results.filter(function(result: string) {
                          return (result.length == options.numOfWords);
                      });
                  }
                  if (!!options.numOfReturn) {
                      results = results.slice(0, options.numOfReturn);
                  }
                  callback(results, undefined);
                  break;
              case 403:
                  callback(undefined, new Error("access denied"));
                  break;
              case 503:
                  callback(undefined, new Error("can't connect to recognition server"));
                  break;
          }
      }
  });
  xhr.open("POST", "https://inputtools.google.com/request?itc=en-t-i0-handwrit&app=mobilesearch");
  xhr.setRequestHeader("content-type", "application/json");
  xhr.send(data);
}

export { HandwritingRecogniser };