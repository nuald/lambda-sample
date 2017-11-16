$(function () {
  var dashPool = [null, "1", "3, 3"];
  var colorPool = {
    "hsl(210, 100%, 50%)" : null,
    "hsl(240, 100%, 50%)" : null,
    "hsl(270, 100%, 50%)" : null,
    "hsl(300, 100%, 50%)" : null,
    "hsl(330, 100%, 50%)" : null,
    "hsl(360, 100%, 50%)" : null,
    "hsl(30,  100%, 50%)" : null,
    "hsl(120, 100%, 20%)" : null
  };
  var margin = {
    top: 50,
    right: 20,
    bottom: 0,
    left: 50
  };
  var legendRectSize = 18;
  var legendSpacing = 4;

  var colorMap = {};

  function getColor(key) {
    if (typeof colorMap[key] === 'undefined') {
      for (var colorKey in colorPool) {
        if (!colorPool[colorKey]) {
          colorPool[colorKey] = key;
          colorMap[key] = colorKey;
          break;
        }
      }
    }
    return colorMap[key];
  }

  function render(graph, src, nameKey, xKey, yKeys) {
    var vis = d3.select(graph),
      width = vis.attr('width'),
      height = vis.attr('height');

    $.getJSON(src, function(data) {
      function getYMin() {
        return d3.min(data, function(d) {
          return d3.min(yKeys, function(yKey) { return d[yKey] });
        });
      }

      function getYMax() {
        return d3.max(data, function(d) {
          return d3.max(yKeys, function(yKey) { return d[yKey] });
        });
      }

      var min = getYMin(),
        max = getYMax(),
        offset = max - min,
        currentOffset = 0,
        offsetMap = {};

      function getOffset(key) {
        if (typeof offsetMap[key] === 'undefined') {
          offsetMap[key] = currentOffset;
          currentOffset += 1.1 * offset;
        }
        return offsetMap[key];
      }

      yKeys.forEach(function(yKey, j) {
        for (var i = 0; i < data.length; ++i) {
          var entry = data[i];
          var offset = getOffset(entry[nameKey]);
          var m = offsetMap;
          entry[yKey] += offset;
        }
      });

      var xScale = d3.scaleLinear().range([margin.left, width - margin.right])
          .domain([d3.min(data, function(d) {
            return d[xKey];
          }), d3.max(data, function(d) {
            return d[xKey];
          })]),
        yScale = d3.scaleLinear().range([height - margin.top, margin.bottom])
          .domain([getYMin(), getYMax()]),
        xAxis = d3.axisBottom(xScale),
        yAxis = d3.axisLeft(yScale),
        lineGenForKey = function(yKey) {
          return d3.line()
            .x(function(d) { return xScale(d[xKey]); })
            .y(function(d) { return yScale(d[yKey]); })
            .curve(d3.curveLinear)
        },
        dataGroup = d3.nest()
          .key(function(d) {
            return d[nameKey];
          })
          .entries(data),
        lSpace = width / dataGroup.length;

      vis.selectAll('.graph, .legend, .axis').remove();

      vis.append("g")
        .attr("class", "x axis")
        .attr("transform", "translate(0," + (height - margin.top) + ")")
        .call(xAxis);
      vis.append("g")
        .attr("class", "y axis")
        .attr("transform", "translate(" + (margin.left) + ",0)")
        .call(yAxis);

      dataGroup.forEach(function(d, i) {
        var color = getColor(d.key);

        yKeys.forEach(function(yKey, i) {
          var lineGen = lineGenForKey(yKey);
          vis.append('path')
            .attr('class', 'graph')
            .attr('d', lineGen(d.values))
            .attr('stroke', color)
            .attr('stroke-width', 2)
            .attr('stroke-dasharray', dashPool[i])
            .attr('fill', 'none');
        });

        var legend = vis.append('g')
          .attr('class', 'legend')
          .attr('transform', function() {
            var horz = (lSpace / 2) + i * lSpace;
            var vert = height - legendRectSize;
            return 'translate(' + horz + ',' + vert + ')';
          });
        legend.append('rect')
          .attr('width', legendRectSize)
          .attr('height', legendRectSize)
          .style('fill', color)
          .style('stroke', color);
        legend.append('text')
          .attr('x', legendRectSize + legendSpacing)
          .attr('y', legendRectSize - legendSpacing)
          .text(d.key);
      });
    });
  }

  var perfChartWidth = 15, perfOffsetX = perfChartWidth * 4;
  var perfHistory = [];

  var perfMargin = {
    top: 10,
    right: 0,
    bottom: 10,
    left: 20
  };
  var vis = d3.select('#response'),
    width = vis.attr('width'),
    height = vis.attr('height');

  var chart = d3.box()
    .whiskers(iqr(1.5))
    .width(perfChartWidth)
    .height(height - perfMargin.top - perfMargin.bottom);

  function drawBox(data) {
    vis.selectAll("g").remove();

    var min = d3.min(data, function(d) { return d3.min(d); });
    var max = d3.max(data, function(d) { return d3.max(d); });

    chart.domain([min, max]);

    for (var i = 0; i < data.length; ++i) {
      var chartData = data[i];
      var translateX = perfMargin.left + i * perfOffsetX;
      vis.append("g").data([chartData])
        .attr("transform", "translate(" + translateX + "," + perfMargin.top + ")")
        .call(chart);
    }
  }

  // Returns a function to compute the interquartile range.
  function iqr(k) {
    return function(d, i) {
      var q1 = d.quartiles[0],
          q3 = d.quartiles[2],
          iqr = (q3 - q1) * k,
          i = -1,
          j = d.length;
      while (d[++i] < q1 - iqr);
      while (d[--j] > q3 + iqr);
      return [i, j];
    };
  }

  $("#evaluate").click(function() {
    $(".alert").alert('close');
    $('#response').LoadingOverlay('show');
    $.getJSON("perf", function(data) {
      perfHistory.unshift(data.timings);
      drawBox(perfHistory);
      var stats = data.actorStats;
      var html = "<table class='table'><thead><tr><th>Actor</th><th>Load (%)</th></tr></thead><tbody>";
      for (var key in stats) {
        if (stats.hasOwnProperty(key)) {
          var value = Math.floor(stats[key] * 100);
          html += "<tr><td>" + key + "</td><td>" + value + "</td></tr>";
        }
      }
      html += "</tbody></table>";
      $("#actors-load").html(html);
      $('#response').LoadingOverlay('hide');
    }).fail(function(jqxhr, textStatus, error) {
      var err = "Request Failed: " + error;
      var html = "<div class='alert alert-danger alert-dismissible' role='alert'>"
       + err
       + "  <button type='button' class='close' data-dismiss='alert' aria-label='Close'>"
       + "    <span aria-hidden='true'>&times;</span>"
       + "  </button>"
       + "</div>";
      $("#alerts").html(html);
      $(".alert").alert();
      $('#response').LoadingOverlay('hide');
    });
  });

  d3.interval(function() {
    render('#entries', 'mqtt', 'sensor', 'ts', ['value']);
    render('#history', 'history', 'name', 'ts', ['avgAnomaly', 'fullAnomaly', 'fastAnomaly']);
  }, 1000);

  $.LoadingOverlaySetup({
    color           : "rgba(230, 230, 230, 0.8)",
    maxSize         : "60px",
    minSize         : "20px",
    resizeInterval  : 0,
    size            : "50%"
  });
});
