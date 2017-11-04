$(function () {
  $("input[type='radio']").click(function() {
    $.ajax({
      url: "update",
      type: "post",
      data: $('form').serialize()
    });
  });
});
