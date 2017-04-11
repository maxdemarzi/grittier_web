$(document).ready(function() {

    $('#status').submit(function(event) {
        var formData = { 'status' : $('textarea[name=status]').val() };
        $.ajax({
            type        : 'POST',
            url         : '/post',
            data        : formData,
            dataType    : 'json',
            encode      : true
        })
            .done(function(data) {
                console.log(data);
            });
        event.preventDefault();
    });

});
