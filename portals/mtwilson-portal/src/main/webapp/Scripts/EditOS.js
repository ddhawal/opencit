$(function() {
	$('#mainEditOSDisplayDiv').prepend(disabledDiv);
	sendJSONAjaxRequest(false, 'getData/getAllOSList.html', null, fnUpdateEditOSTable, null);
});

/*method to display edit table*/
function fnUpdateEditOSTable(responseJSON) {
	$('#disabledDiv').remove();
	if (responseJSON.result) {
		$('#mainEditTable').show();
		fuCreateEditOSTable(responseJSON.OSDataVo);
		applyPagination('editOSPaginationDiv',responseJSON.noOfPages,fngetOSNextPageForEdit,1);
	}else {
		$('#errorEditOS').html(getHTMLEscapedMessage(responseJSON.message));
	}
}

/*Method to create dynamic edit table for MLE*/
function fuCreateEditOSTable(osData) {
	var str = "";
	$('#editOSContentDiv table tbody').html("");
	for ( var items in osData) {
                if(items != parseInt(items)) {
                        continue;
                }
		var classValue = null; 
		if(items % 2 === 0){classValue='oddRow';}else{classValue='evenRow';}
		str+='<tr class="'+classValue+'">'+
		'<td class="row1"><a href="javascript:;" onclick="fnEditOSInfo(this)" data-toggle="tooltip" title="Edit OS"><span class="glyphicon glyphicon-edit"></span></a>&nbsp;<a href="javascript:;" onclick="fnDeleteOSInfo(this)" data-toggle="tooltip" title="Delete OS"><span class="glyphicon glyphicon-trash"></span></a></td>'+
		'<td class="row1" name="'+ escapeForHTMLAttributes(osData[items].osName) +'" value="'+ escapeForHTMLAttributes(osData[items].osName) +'" id="osName">'+ getHTMLEscapedMessage(osData[items].osName) +'</td>'+
		'<td class="row1" name="'+ escapeForHTMLAttributes(osData[items].osVersion) +'" value="'+ escapeForHTMLAttributes(osData[items].osVersion) +'" id="osVer">'+ getHTMLEscapedMessage(osData[items].osVersion) +'</td>';
		var val1 = osData[items].osDescription == undefined ? '' : osData[items].osDescription;
		str+='<td class="row1" name="'+ escapeForHTMLAttributes(val1) +'" value="'+ escapeForHTMLAttributes(val1) +'" id="osDec">'+ getHTMLEscapedMessage(val1) +'&nbsp;</td>';
	}
	$('#editOSContentDiv table tbody').html(str);
}

/*function to get the value for give page no*/
function fngetOSNextPageForEdit(pageNo) {
	$('#messageSpace').html('');
	$('#mainEditTable').prepend(disabledDiv);
	sendJSONAjaxRequest(false, 'getData/getViewOSForPageNo.html', "pageNo="+pageNo, fnUpdateEditOSTableForPage, null);
}

function fnUpdateEditOSTableForPage(responseJSON) {
	$('#disabledDiv').remove();
	if (responseJSON.result) {
		fuCreateEditOSTable(responseJSON.OSDataVo);
	}else {
		$('#errorEditOS').html(getHTMLEscapedMessage(responseJSON.message));
	}
}

