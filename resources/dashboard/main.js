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
  var margins = {
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

    d3.json(src).get(function(error, data) {
      if (error) {
        throw error;
      }

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

      var xScale = d3.scaleLinear().range([margins.left, width - margins.right])
          .domain([d3.min(data, function(d) {
            return d[xKey];
          }), d3.max(data, function(d) {
            return d[xKey];
          })]),
        yScale = d3.scaleLinear().range([height - margins.top, margins.bottom])
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
        .attr("transform", "translate(0," + (height - margins.top) + ")")
        .call(xAxis);
      vis.append("g")
        .attr("class", "y axis")
        .attr("transform", "translate(" + (margins.left) + ",0)")
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

  d3.interval(function() {
    render('#entries', 'mqtt', 'sensor', 'ts', ['value']);
    render('#history', 'history', 'name', 'ts', ['avgAnomaly', 'fullAnomaly', 'fastAnomaly']);
  }, 1000);
});
