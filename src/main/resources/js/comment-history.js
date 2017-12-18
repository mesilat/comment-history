define('com.mesilat/comment-history', ['com.mesilat/JsDiff'], function(JsDiff){
    function hasCommentHistoryTable($comment){
        return $comment.find('div.com-mesilat-comment-history').length !== 0;
    }
    function switchCommentHistoryTable($comment){
        $comment.find('div.com-mesilat-comment-history').each(function() {
            var $div = $(this);
            if ($div.is(':hidden')) {
                $div.show();
            } else {
                $div.hide();
            }
        });
    }
    function removeCommentHistoryTable($comment){
        $comment.find('div.com-mesilat-comment-history').remove();
    }
    function appendCommentHistoryMenuItem($comment){
        var id = getCommentId($comment);
        if (typeof id === 'undefined') {
            return;
        }
        var $actions = $comment.find('div.comment-actions ul.comment-actions-primary');
        if ($actions.find('li.comment-history').length === 0) {
            $('<li class="comment-history"><a href="#"></a></li>')
                .appendTo($actions)
                .find('a')
                .text(AJS.I18n.getText("com.mesilat.comment-history.report.caption"))
                .on('click', onClickCommentHistoryMenuItem);
        }
    }
    function getCommentId($comment){
        var id = $comment.attr('id');
        if (typeof id === 'undefined') {
            return id;
        }
        return id.substring(id.indexOf('-') + 1);
    }
    function loadCommentHistoryTable($comment){
        $.ajax({
            url: AJS.contextPath() + '/rest/comments-history/1.0/',
            type: 'GET',
            data: {
                commentId: getCommentId($comment)
            },
            dataType: 'json'
        }).done(function (data) {
            var $table = $(Mesilat.CommentHistory.Templates.listCommentVersions({
                data: data
            }));
            $table.appendTo($comment.find('div.comment-body'));
            setTimeout(beautifyCommentHistoryTable,0,data);
        }).fail(function (jqXHR) {
            alert(jqXHR.responseText);
        });
    }
    function beautifyCommentHistoryTable(data){
        data.forEach(function (rec, i) {
            if (i < data.length - 1) {
                var diff = JsDiff['diffWords'](data[i + 1].body, rec.body);
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < diff.length; i++) {
                    if (diff[i].added && diff[i + 1] && diff[i + 1].removed) {
                        var swap = diff[i];
                        diff[i] = diff[i + 1];
                        diff[i + 1] = swap;
                    }
                    var node;
                    if (diff[i].removed) {
                        node = document.createElement('del');
                        node.appendChild(document.createTextNode(diff[i].value));
                    } else if (diff[i].added) {
                        node = document.createElement('ins');
                        node.appendChild(document.createTextNode(diff[i].value));
                    } else {
                        node = document.createTextNode(diff[i].value);
                    }
                    fragment.appendChild(node);
                }
                $('tr.com-mesilat-comment-' + rec.contentId + ' td.com-mesilat-comment-body')
                    .empty()
                    .append($(fragment));
            }
        });
    }
    function onClickCommentHistoryMenuItem(e) {
        var $comment = $(e.target).closest('div.comment');
        e.preventDefault();
        e.stopPropagation();

        if (hasCommentHistoryTable($comment)) {
            switchCommentHistoryTable($comment);
        } else {
            loadCommentHistoryTable($comment);
        }
    }

    return {
        init: function(){
            $('#comments-section div.comment').each(function(){
                appendCommentHistoryMenuItem($(this));
            });
        },
        reload: function() {
            $('#comments-section div.comment').each(function(){
                removeCommentHistoryTable($(this));
                appendCommentHistoryMenuItem($(this));
            });
        }
    };
});

$(function () {
    var commentHistory = require('com.mesilat/comment-history');
    commentHistory.init();

    AJS.bind("rte.init.ui", function () {
        setTimeout(function(){
            Confluence.Editor.addSaveHandler(function(e){
                setTimeout(function(){
                    console.log('Reload all comment history');
                    commentHistory.reload();
                }, 1000);
            });
        }, 100);
    });
});