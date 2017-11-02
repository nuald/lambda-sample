$(function () {
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
    bottom: 50,
    left: 50
  };
  var legendRectSize = 18;
  var legendSpacing = 4;

  var colorMap = {};

  function getColor(key) {
    if (!colorMap[key]) {
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

  var vis = d3.select("#visualisation"),
    width = vis.attr('width'),
    height = vis.attr('height');

  function render() {
    d3.json("mqtt").get(function(error, data) {
      if (error) {
        throw error;
      }

      var xScale = d3.scaleLinear().range([margins.left, width - margins.right])
          .domain([d3.min(data, function(d) {
            return d.ts;
          }), d3.max(data, function(d) {
            return d.ts;
          })]),
        yScale = d3.scaleLinear().range([height - margins.top, margins.bottom])
          .domain([d3.min(data, function(d) {
            return d.value;
          }), d3.max(data, function(d) {
            return d.value;
          })]),
        xAxis = d3.axisBottom(xScale),
        yAxis = d3.axisLeft(yScale),
        lineGen = d3.line()
          .x(function(d) {
            return xScale(d.ts);
          })
          .y(function(d) {
            return yScale(d.value);
          })
          .curve(d3.curveLinear),
        dataGroup = d3.nest()
          .key(function(d) {
            return d.sensor;
          })
          .entries(data),
        lSpace = width / dataGroup.length;

      vis.selectAll('.graph, .legend, .axis').remove();

      vis.append("g")
        .attr("class", "x axis")
        .attr("transform", "translate(0," + (height - margins.bottom) + ")")
        .call(xAxis);
      vis.append("g")
        .attr("class", "y axis")
        .attr("transform", "translate(" + (margins.left) + ",0)")
        .call(yAxis);

      dataGroup.forEach(function(d, i) {
        var color = getColor(d.key);

        vis.append('path')
          .attr('class', 'graph')
          .attr('d', lineGen(d.values))
          .attr('stroke', color)
          .attr('stroke-width', 2)
          .attr('fill', 'none');

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
    render();
  }, 1000);
});
