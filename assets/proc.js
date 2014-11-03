function $(id)
{
	return document.getElementById(id);
}

function newAjax()
{
	return (window.XMLHttpRequest)?(new XMLHttpRequest()):(new ActiveXObject("Microsoft.XMLHTTP"));
}

function ajaxConfigAndSend(ajax, cmd, handler)
{
	ajax.onreadystatechange=function()
	{
		if(ajax.readyState==4 && ajax.status==200)
			handler();
	}
	ajax.open("GET",encodeURI(cmd),true);
	ajax.setRequestHeader("If-Modified-Since","0");//FUCK IE!
	ajax.send();
}

function ajaxGet(url, callBack)
{
	var ajax=newAjax();
	function recvJson()
	{
		callBack(eval("("+ajax.responseText+")"));
	}
	ajaxConfigAndSend(ajax,url,recvJson);
}

function getElementPos(element)
{
	var pos=new Object;
	pos.top = element.offsetTop;
	pos.left = element.offsetLeft;
	var current = element.offsetParent;
	while (current !== null)
	{
		pos.top += current.offsetTop;
		pos.left += current.offsetLeft;
		current = current.offsetParent;
	}
	return pos;
}

