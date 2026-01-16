package robobrowser

import rapid.Task

object AntiCaptcha {
  extension (browser: RoboBrowser) {
    /**
     * Injects the AntiCaptcha script into the document of the current browser session. 
     * This method embeds the API key into the page and loads the AntiCaptcha script 
     * to enable captcha solving functionality.
     *
     * @param apiKey the API key for the AntiCaptcha service; it is used for authentication and script functionality.
     * @return a `Task[Unit]` that represents the completion of the script injection process.
     */
    def injectAntiCaptcha(apiKey: String): Task[Unit] = browser.eval(
      s"""(function(){
         |    var d = document.getElementById("anticaptcha-imacros-account-key");
         |    if (!d) {
         |        d = document.createElement("div");
         |        d.innerHTML = "$apiKey";
         |        d.style.display = "none";
         |        d.id = "anticaptcha-imacros-account-key";
         |        document.body.appendChild(d);
         |    }
         |
         |    var s = document.createElement("script");
         |    s.src = "https://cdn.antcpt.com/imacros_inclusion/recaptcha.js?" + Math.random();
         |    document.body.appendChild(s);
         |})();""".stripMargin
    ).unit
  }
}
