package org.matrix.chromext.script

const val GM_addStyle =
    """
function GM_addStyle(css) {
	const style = document.createElement("style");
	style.setAttribute("type", "text/css");
	style.textContent = css;

	const head = document.querySelector("head");
	if (head === undefined) {
		document.documentElement.appendChild(style);
	} else {
		head.appendChild(style);
	}
};
"""

const val GM_addElement =
    """
function GM_addElement(parent_node, tag_name, attributes) {
	const element = document.createElement(tag_name);
	for (const [key, value] of Object.entries(attributes)) {
		if (key != "textContent") {
			element.setAttribute(key, value);
		} else {
			element.textContent = value;
		};
	};
	if (parent_node === undefined) {
		document.documentElement.appendChild(element);
	} else {
		parent_node.appendChild(element);
	};
};

function GM_addElement(tag_name, attributes) {
	GM_addElement(document.head || document.body, tag_name, attributes);
};
"""

fun encodeScript(script: Script): String? {
  var code = script.code
  if (script.encoded) {
    return null
  }

  val shouldWrap: Boolean = code.contains("\\`")

  var backtrick = ""
  if (shouldWrap) {
    // Encode source code by simple replacement
    val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
    backtrick = List(16) { alphabet.random() }.joinToString("")
    code = code.replace("`", backtrick)
    code = """Function(ChromeXt_decode(`${code}`))();"""
  }

  when (script.runAt) {
    RunAt.START -> {}
    RunAt.END -> code = """document.addEventListener("DOMContentLoaded",()=>{${code}});"""
    RunAt.IDLE -> code = """window.onload=()=>{${code}};"""
  }

  val imports =
      script.require
          .map {
            if (it != "") {
              """await import("${it}")"""
            } else {
              it
            }
          }
          .joinToString(separator = ";")
  if (imports != "") {
    code = """(async ()=>{${imports};${code}})();"""
  }

  script.grant.forEach {
    val function = it
    if (function == "") {
      return@forEach
    }
    when (function) {
      "GM_addStyle" -> code = GM_addStyle + code
      "GM_addElement" -> code = GM_addElement + code
      "unsafeWindow" -> code = "const unsafeWindow = window;" + code
      "GM_log" -> code = "const GM_log = console.log.bind(console);" + code
      "GM_deleteValue" ->
          code = "const GM_deleteValue = localStorage.removeItem.bind(localStorage);" + code
      "GM_setValue" -> code = "const GM_setValue = localStorage.setItem.bind(localStorage);" + code
      "GM_getValue" -> code = "const GM_getValue = localStorage.getItem.bind(localStorage);" + code
      "GM_listValues" ->
          code =
              "const GM_listValues = ()=> [...Array(localStorage.length).keys()].map(x=>localStorage.key(x));" +
                  code
      else ->
          code =
              """function ${function}(...args) {console.error("${function} is not implemented in ChromeXt yet, called with", args)};""" +
                  code
    }
  }

  if (shouldWrap) {
    // Add decode function, and it finally contains only three backtricks in total
    code = """function ChromeXt_decode(src) {return src.replaceAll("${backtrick}", "`");};""" + code
  }
  return code
}

// The Charset setting is hard-coded inside
// src/third_party/blink/renderer/core/html/parser/text_document_parser.cc
// not able to solve it for now
const val promptInstallUserScript: String =
    """
let old_script = "";
let asked = false;

function installScript() {
	let script = document.querySelector("body > pre").innerText;
	if (script != old_script) {
		old_script = script;
		let install = confirm("Allow ChromeXt to install / update this userscript?");
		if (install) {
			console.debug(JSON.stringify({"action": "installScript", "payload": script}));
		}
	}
};

function editor() {
	const js = document.createElement("script");
	const meta = document.createElement("meta");
	const style = document.createElement("style");
	js.setAttribute("type", "module");
	js.textContent = "import {highlightElement} from 'https://unpkg.com/@speed-highlight/core/dist/index.js';highlightElement(document.querySelector('body > pre'), 'js', {hideLineNumbers: true});";
	style.setAttribute("type", "text/css");
	meta.setAttribute("name", "viewport");
	meta.setAttribute("content", "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no");
	style.textContent = `
	@import url('https://unpkg.com/@speed-highlight/core/dist/themes/default.css');
	body {
		margin: 0;
	}
	[class*="shj-lang-"] {
		border-radius: 0;
		color: #abb2bf;
		background: #161b22;
		font: 1.2em monospace;
		padding: 8px 5px;
		margin: 0;
		width: 100vw;
		word-break: break-word;
	}
	[class*="shj-lang-"]:before {color: #6f9aff}
	.shj-syn-deleted,
	.shj-syn-err,
	.shj-syn-var {color: #e06c75}
	.shj-syn-section,
	.shj-syn-oper,
	.shj-syn-kwd {color: #c678dd}
	.shj-syn-class {color: #e5c07b}
	.shj-numbers,
	.shj-syn-cmnt {color: #76839a}
	.shj-syn-insert {color: #98c379}
	.shj-syn-type {color: #56b6c2}
	.shj-syn-num,
	.shj-syn-bool {color: #d19a66}
	.shj-syn-str,
	.shj-syn-func {color: #61afef}
	`;
	document.head.appendChild(meta);
	document.head.appendChild(style);
	document.body.appendChild(js);
	document.querySelector("body > pre").setAttribute("contenteditable", true);
	setTimeout(installScript, 500);
};

if (document.readyState == "complete") {
	editor();
} else {
	window.onload = editor;
};

addEventListener("contextmenu", () => {
	addEventListener("click", (event) => {
		if (!asked) {
			event.preventDefault();
			setTimeout(()=>{asked=false;installScript();}, 100);
			asked = true;
		}
	}, {once: true});
});
"""

const val erudaToggle =
    """
if (typeof globalThis.eruda != "undefined") {
	if (eruda._isInit) {
		eruda.hide();
		eruda.destroy();
	} else {
		eruda.init();
		eruda.show();
	}
}
"""

const val erudaFontFix =
    """
const erudaroot = document.querySelector("#eruda").shadowRoot;
const style = document.createElement("style");
style.setAttribute("type", "text/css");
style.textContent = `
[class^='eruda-icon-']:before {
	font-size: 14px;
}
.eruda-icon-arrow-left:before {
  content: '←';
}
.eruda-icon-arrow-right:before {
  content: '→';
}
.eruda-icon-clear:before {
  content: '✖';
  font-size: 17px;
}
.eruda-icon-compress:before {
  content: '🗎';
}
.eruda-icon-copy:before {
  content: '📋';
}
.eruda-icon-delete:before {
  content: '⌫';
}
.eruda-icon-error:before {
  content: '✗';
}
.eruda-icon-expand:before {
  content: '⌄';
}
.eruda-icon-eye:before {
  content: '👁';
}
.eruda-icon-filter:before {
  content: 'Ⴤ';
  font-size: 15px;
  font-weight: bold;
}
.eruda-icon-play:before {
  content: '▷';
}
.eruda-icon-record:before {
  content: '●';
}
.eruda-icon-refresh:before {
  content: '↻';
  font-size: 18px;
  font-weight: bold;
}
.eruda-icon-reset:before {
  content: '↺';
}
.eruda-icon-search:before {
  content: '🔍';
}
.eruda-icon-select:before {
  content: '⇖';
  font-size: 26px;
}
.eruda-icon-tool:before {
  content: '⚙';
  font-size: 30px;
}
.eruda-icon-warn:before {
  content: '⚠';
}
.luna-dom-viewer-icon-arrow-right:before {
  content: "► ";
  font-size: 16px;
}
.luna-dom-viewer-icon-arrow-down:before {
  content: '▼';
  font-size: 9px;
}
`;
erudaroot.append(style);
"""
